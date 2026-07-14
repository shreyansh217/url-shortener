package com.urlshortener.dto;

import java.time.Instant;

/**
 * Response body for GET /api/stats/{code}.
 *
 * @param shortCode   The short code.
 * @param shortUrl    The fully qualified short URL.
 * @param originalUrl The target URL.
 * @param hitCount    Number of times this short link has been accessed.
 * @param isCustom    Whether the code was a user-supplied alias.
 * @param createdAt   When the mapping was created.
 * @param expiresAt   Optional expiry timestamp; null means never expires.
 */
public record StatsResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        long hitCount,
        boolean isCustom,
        Instant createdAt,
        Instant expiresAt
) {}
