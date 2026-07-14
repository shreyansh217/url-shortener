package com.urlshortener.exception;

/**
 * Thrown when a short code or alias cannot be found in the datastore.
 * Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class UrlNotFoundException extends RuntimeException {
    private final String code;

    public UrlNotFoundException(String code) {
        super("No URL mapping found for code: " + code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
