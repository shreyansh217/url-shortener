package com.urlshortener.validation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

/**
 * Validates incoming URLs before shortening.
 *
 * <h2>Rules enforced</h2>
 * <ol>
 *   <li>Not null / not blank</li>
 *   <li>Maximum 2048 characters (browser and server limits)</li>
 *   <li>Parseable as a valid URI</li>
 *   <li>Scheme must be {@code http} or {@code https}</li>
 *   <li>Host must be non-empty</li>
 *   <li>Unless {@code app.allow-localhost-urls=true}, rejects localhost and RFC-1918 private IP ranges</li>
 * </ol>
 *
 * <h2>What we deliberately do NOT validate</h2>
 * <ul>
 *   <li>Reachability – we don't make an outbound HTTP request. A URL can be valid but temporarily down.</li>
 *   <li>Content/safety – out of scope for this exercise; in production you'd integrate a safe-browsing API.</li>
 * </ul>
 */
@Component
public class UrlValidator {

    private static final int MAX_URL_LENGTH = 2048;
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    /** Private/loopback IP prefixes to reject in production mode. */
    private static final List<String> PRIVATE_IP_PREFIXES = List.of(
            "127.", "10.", "192.168.", "172.16.", "172.17.", "172.18.", "172.19.",
            "172.20.", "172.21.", "172.22.", "172.23.", "172.24.", "172.25.",
            "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.",
            "0.", "169.254."
    );

    private final boolean allowLocalhostUrls;

    public UrlValidator(@Value("${app.allow-localhost-urls:false}") boolean allowLocalhostUrls) {
        this.allowLocalhostUrls = allowLocalhostUrls;
    }

    /**
     * Validate the given URL string.
     *
     * @param url the URL to validate
     * @throws InvalidUrlException with a human-readable message if validation fails
     */
    public void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidUrlException("URL must not be blank");
        }

        if (url.length() > MAX_URL_LENGTH) {
            throw new InvalidUrlException(
                    String.format("URL exceeds maximum length of %d characters (got %d)", MAX_URL_LENGTH, url.length()));
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("URL is not a valid URI: " + e.getReason());
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new InvalidUrlException(
                    "URL scheme must be 'http' or 'https', got: '" + scheme + "'");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("URL must have a valid host");
        }

        if (!allowLocalhostUrls) {
            rejectPrivateHosts(host);
        }
    }

    private void rejectPrivateHosts(String host) {
        String lower = host.toLowerCase();
        if (lower.equals("localhost") || lower.endsWith(".localhost")) {
            throw new InvalidUrlException("Localhost URLs are not allowed");
        }
        for (String prefix : PRIVATE_IP_PREFIXES) {
            if (lower.startsWith(prefix)) {
                throw new InvalidUrlException("Private/loopback IP addresses are not allowed: " + host);
            }
        }
    }

    /**
     * Runtime exception thrown when URL validation fails.
     * Caught by {@link com.urlshortener.exception.GlobalExceptionHandler} and mapped to HTTP 400.
     */
    public static class InvalidUrlException extends RuntimeException {
        public InvalidUrlException(String message) {
            super(message);
        }
    }
}
