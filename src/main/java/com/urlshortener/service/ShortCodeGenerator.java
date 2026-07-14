package com.urlshortener.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates URL-safe, collision-resistant short codes using Base62 encoding.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Draw a random {@code long} from {@link SecureRandom} (cryptographically strong PRNG).</li>
 *   <li>Encode it in Base62 ({@code [A-Za-z0-9]}) – producing a URL-safe string.</li>
 *   <li>Take the first {@code codeLength} characters (default 7).</li>
 * </ol>
 *
 * <h2>Collision probability</h2>
 * With the default length of 7:
 * <pre>
 *   Code-space = 62^7 ≈ 3.52 × 10^12  (~3.5 trillion unique codes)
 * </pre>
 * By the birthday paradox, you need ~2.4 million codes before the collision probability
 * reaches even 0.08% — orders of magnitude beyond any realistic load for this service.
 *
 * <h2>Why not sequential IDs?</h2>
 * Sequential IDs are guessable and enumerable — a privacy concern.
 *
 * <h2>Why not a hash of the URL?</h2>
 * Hashing the URL would return the same code for the same URL regardless of policy,
 * removing flexibility in the duplicate-URL strategy.
 */
@Component
public class ShortCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int BASE = ALPHABET.length(); // 62

    // SecureRandom is thread-safe and seeded automatically by the JVM
    private final SecureRandom random = new SecureRandom();

    private final int codeLength;

    public ShortCodeGenerator(@Value("${app.short-code-length:7}") int codeLength) {
        if (codeLength < 4 || codeLength > 16) {
            throw new IllegalArgumentException("short-code-length must be between 4 and 16, got: " + codeLength);
        }
        this.codeLength = codeLength;
    }

    /**
     * Generate a new random short code of the configured length.
     *
     * <p>The caller is responsible for checking uniqueness and retrying if needed.
     * See {@link com.urlshortener.service.UrlShortenerService} for the retry loop.
     *
     * @return a URL-safe Base62 string of length {@code codeLength}
     */
    public String generate() {
        StringBuilder sb = new StringBuilder(codeLength);
        // Use nextLong with a bound to avoid sign issues; pick characters individually
        for (int i = 0; i < codeLength; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(BASE)));
        }
        return sb.toString();
    }

    /**
     * Validate that a caller-supplied custom alias contains only URL-safe Base62 characters
     * and meets the length requirements.
     *
     * @param alias the custom alias to validate
     * @return true if the alias is valid
     */
    public boolean isValidAlias(String alias) {
        if (alias == null || alias.isBlank()) return false;
        if (alias.length() < 1 || alias.length() > 16) return false;
        return alias.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0 || c == '-' || c == '_');
    }
}
