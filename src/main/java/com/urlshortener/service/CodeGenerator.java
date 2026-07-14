package com.urlshortener.service;

/**
 * Contract for generating and validating short codes.
 *
 * <p>Extracting this interface serves two purposes:
 * <ol>
 *   <li>Allows Mockito to mock via JDK dynamic proxy instead of byte-buddy class instrumentation,
 *       which is more reliable on newer JVMs (Java 21+).</li>
 *   <li>Makes the generation strategy swappable without touching the service layer.</li>
 * </ol>
 */
public interface CodeGenerator {

    /**
     * Generate a new random short code.
     *
     * @return a URL-safe string of the configured length
     */
    String generate();

    /**
     * Validate that a caller-supplied custom alias is syntactically acceptable.
     *
     * @param alias the alias to validate
     * @return true if valid
     */
    boolean isValidAlias(String alias);
}
