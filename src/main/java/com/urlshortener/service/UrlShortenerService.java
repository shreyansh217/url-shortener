package com.urlshortener.service;

import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.dto.StatsResponse;
import com.urlshortener.exception.AliasAlreadyTakenException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.repository.UrlMappingRepository;
import com.urlshortener.validation.UrlValidator;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Core business logic for the URL shortener service.
 *
 * <h2>Duplicate URL Policy</h2>
 * Controlled by {@code app.duplicate-url-policy} in application.properties:
 * <ul>
 *   <li><b>RETURN_EXISTING</b> (default) – if the same original URL has already been shortened,
 *       return the existing mapping. {@code existing=true} in the response signals this to the caller.
 *       Rationale: most clients expect idempotent shortening; returning a new code for the same
 *       URL would waste code-space and confuse analytics.</li>
 *   <li><b>CREATE_NEW</b> – always generate a fresh code, allowing multiple short links
 *       per original URL. Useful for per-campaign tracking.</li>
 * </ul>
 *
 * <h2>Custom Alias Flow</h2>
 * When {@code customAlias} is present the alias bypasses the generator entirely.
 * A 409 is returned immediately if the alias is already taken — there is no silent fallback
 * to an auto-generated code, because that would silently ignore the caller's intent.
 *
 * <h2>Collision Handling</h2>
 * The generator draws from a ~3.5 trillion code-space. In the astronomically rare event of
 * a collision, the service retries up to {@code MAX_RETRIES} times before failing.
 */
@Service
public class UrlShortenerService {

    private static final Logger log = LoggerFactory.getLogger(UrlShortenerService.class);
    private static final int MAX_RETRIES = 5;

    private final UrlMappingRepository repository;
    private final CodeGenerator generator;
    private final UrlValidation urlValidator;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.duplicate-url-policy:RETURN_EXISTING}")
    private String duplicateUrlPolicy;

    public UrlShortenerService(UrlMappingRepository repository,
                               CodeGenerator generator,
                               UrlValidation urlValidator) {
        this.repository   = repository;
        this.generator    = generator;
        this.urlValidator = urlValidator;
    }

    /**
     * Shorten a URL, honouring the duplicate-URL policy and custom alias if provided.
     *
     * @param request the shorten request
     * @return the shorten response with short code and metadata
     */
    @Transactional
    public ShortenResponse shorten(ShortenRequest request) {
        // Step 1: Validate the URL
        urlValidator.validate(request.url());

        // Step 2: Handle custom alias path
        if (request.customAlias() != null && !request.customAlias().isBlank()) {
            return handleCustomAlias(request);
        }

        // Step 3: Duplicate-URL check (auto-generated codes only)
        if ("RETURN_EXISTING".equalsIgnoreCase(duplicateUrlPolicy)) {
            var existing = repository.findFirstByOriginalUrl(request.url());
            if (existing.isPresent()) {
                log.debug("Duplicate URL detected, returning existing mapping: {}", existing.get().getShortCode());
                return toResponse(existing.get(), true);
            }
        }

        // Step 4: Generate a unique short code with collision retry
        String code = generateUniqueCode();

        UrlMapping mapping = UrlMapping.builder()
                .shortCode(code)
                .originalUrl(request.url())
                .isCustom(false)
                .build();

        UrlMapping saved = repository.save(mapping);
        log.info("Created new short code '{}' for URL: {}", code, request.url());
        return toResponse(saved, false);
    }

    /**
     * Resolve a short code to its original URL.
     * Increments the hit counter as a side-effect.
     *
     * @param code the short code
     * @return the original URL
     * @throws UrlNotFoundException if the code does not exist
     */
    @Transactional
    public String resolve(String code) {
        UrlMapping mapping = repository.findByShortCode(code)
                .orElseThrow(() -> new UrlNotFoundException(code));

        repository.incrementHitCount(code);
        log.debug("Resolved '{}' → '{}' (hit #{})", code, mapping.getOriginalUrl(), mapping.getHitCount() + 1);
        return mapping.getOriginalUrl();
    }

    /**
     * Retrieve analytics stats for a given short code.
     *
     * @param code the short code
     * @return stats response
     * @throws UrlNotFoundException if the code does not exist
     */
    public StatsResponse getStats(String code) {
        UrlMapping mapping = repository.findByShortCode(code)
                .orElseThrow(() -> new UrlNotFoundException(code));
        return toStatsResponse(mapping);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ShortenResponse handleCustomAlias(ShortenRequest request) {
        String alias = request.customAlias().trim();

        // Validate alias character set
        if (!generator.isValidAlias(alias)) {
            throw new UrlValidator.InvalidUrlException(
                    "Custom alias '" + alias + "' contains invalid characters. Use only [A-Za-z0-9_-].");
        }

        // Custom aliases are NEVER deduplicated against existing ones – if someone explicitly
        // asks for "my-alias" and it's taken, we fail loudly rather than silently changing it.
        if (repository.existsByShortCode(alias)) {
            throw new AliasAlreadyTakenException(alias);
        }

        UrlMapping mapping = UrlMapping.builder()
                .shortCode(alias)
                .originalUrl(request.url())
                .isCustom(true)
                .build();

        UrlMapping saved = repository.save(mapping);
        log.info("Created custom alias '{}' for URL: {}", alias, request.url());
        return toResponse(saved, false);
    }

    private String generateUniqueCode() {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            String code = generator.generate();
            if (!repository.existsByShortCode(code)) {
                return code;
            }
            log.warn("Short code collision on attempt {}: '{}'. Retrying...", attempt, code);
        }
        throw new IllegalStateException(
                "Failed to generate a unique short code after " + MAX_RETRIES + " attempts. " +
                "This is extremely unlikely — investigate DB state.");
    }

    private ShortenResponse toResponse(UrlMapping mapping, boolean existing) {
        return new ShortenResponse(
                mapping.getShortCode(),
                baseUrl + "/" + mapping.getShortCode(),
                mapping.getOriginalUrl(),
                existing,
                mapping.getCreatedAt()
        );
    }

    private StatsResponse toStatsResponse(UrlMapping mapping) {
        return new StatsResponse(
                mapping.getShortCode(),
                baseUrl + "/" + mapping.getShortCode(),
                mapping.getOriginalUrl(),
                mapping.getHitCount(),
                mapping.isCustom(),
                mapping.getCreatedAt(),
                mapping.getExpiresAt()
        );
    }
}
