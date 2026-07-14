package com.urlshortener.dto;

import java.time.Instant;

/**
 * Response body for POST /shorten.
 *
 * @param shortCode   The generated (or existing) short code.
 * @param shortUrl    The fully qualified short URL ready to share.
 * @param originalUrl The original long URL.
 * @param existing    True when a pre-existing mapping was returned (duplicate URL policy: RETURN_EXISTING).
 * @param createdAt   Timestamp when the mapping was first created.
 */
public record ShortenResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        boolean existing,
        Instant createdAt
) {}
