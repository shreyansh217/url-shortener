package com.urlshortener.repository;

import com.urlshortener.model.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link UrlMapping}.
 *
 * <p>Exposes the two main lookup patterns:
 * <ol>
 *   <li>By {@code shortCode}  – used on every redirect (GET /{code})</li>
 *   <li>By {@code originalUrl} – used for duplicate-URL detection (POST /shorten)</li>
 * </ol>
 */
@Repository
public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    /**
     * Primary lookup: resolve a short code to its mapping.
     */
    Optional<UrlMapping> findByShortCode(String shortCode);

    /**
     * Duplicate-URL check: find an existing mapping for a given original URL.
     * Used when {@code app.duplicate-url-policy=RETURN_EXISTING}.
     */
    Optional<UrlMapping> findFirstByOriginalUrl(String originalUrl);

    /**
     * Existence check: used by the short-code generator to detect collisions.
     */
    boolean existsByShortCode(String shortCode);

    /**
     * Atomically increment the hit counter for a short code.
     * Executed as a single UPDATE statement – no entity load needed for analytics.
     */
    @Modifying
    @Query("UPDATE UrlMapping u SET u.hitCount = u.hitCount + 1 WHERE u.shortCode = :shortCode")
    void incrementHitCount(@Param("shortCode") String shortCode);
}
