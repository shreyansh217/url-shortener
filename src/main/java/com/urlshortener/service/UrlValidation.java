package com.urlshortener.service;

/**
 * Contract for URL validation.
 *
 * <p>Extracting this interface enables JDK proxy-based mocking in tests on Java 25,
 * where Mockito's byte-buddy inline mock maker cannot resolve the class file version.
 */
public interface UrlValidation {

    /**
     * Validate a URL string.
     *
     * @param url the URL to validate
     * @throws com.urlshortener.validation.UrlValidator.InvalidUrlException if invalid
     */
    void validate(String url);
}
