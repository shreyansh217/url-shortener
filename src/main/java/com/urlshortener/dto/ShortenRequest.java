package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /shorten.
 *
 * @param url         The original URL to shorten (required).
 * @param customAlias Optional custom alias. If provided, it is used as-is (after validation).
 *                    Must be 1–16 characters using [A-Za-z0-9_-].
 */
public record ShortenRequest(
        @NotBlank(message = "url must not be blank")
        String url,

        @Size(min = 1, max = 16, message = "customAlias must be between 1 and 16 characters")
        String customAlias
) {}
