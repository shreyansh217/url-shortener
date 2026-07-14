package com.urlshortener.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UrlValidator}.
 */
class UrlValidatorTest {

    // allowLocalhostUrls=false → production mode
    private final UrlValidator validatorStrict = new UrlValidator(false);
    // allowLocalhostUrls=true → test mode
    private final UrlValidator validatorPermissive = new UrlValidator(true);

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://example.com/path?q=1",
            "https://sub.domain.org/very/long/path#anchor",
            "https://example.com:8443/api"
    })
    void validUrls_passValidation(String url) {
        assertThatNoException().isThrownBy(() -> validatorStrict.validate(url));
    }

    @Test
    void nullUrl_throwsInvalidUrlException() {
        assertThatThrownBy(() -> validatorStrict.validate(null))
                .isInstanceOf(UrlValidator.InvalidUrlException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void blankUrl_throwsInvalidUrlException() {
        assertThatThrownBy(() -> validatorStrict.validate("   "))
                .isInstanceOf(UrlValidator.InvalidUrlException.class);
    }

    @Test
    void urlExceedingMaxLength_throwsInvalidUrlException() {
        String longUrl = "https://example.com/" + "a".repeat(2048);
        assertThatThrownBy(() -> validatorStrict.validate(longUrl))
                .isInstanceOf(UrlValidator.InvalidUrlException.class)
                .hasMessageContaining("maximum length");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com", "mailto:user@example.com", "file:///etc/passwd"})
    void nonHttpSchemes_throwsInvalidUrlException(String url) {
        assertThatThrownBy(() -> validatorStrict.validate(url))
                .isInstanceOf(UrlValidator.InvalidUrlException.class)
                .hasMessageContaining("scheme");
    }

    @Test
    void localhostUrl_rejectedInStrictMode() {
        assertThatThrownBy(() -> validatorStrict.validate("http://localhost:8080/test"))
                .isInstanceOf(UrlValidator.InvalidUrlException.class)
                .hasMessageContaining("Localhost");
    }

    @Test
    void localhostUrl_allowedInPermissiveMode() {
        assertThatNoException().isThrownBy(() -> validatorPermissive.validate("http://localhost:8080/test"));
    }

    @Test
    void privateIpUrl_rejectedInStrictMode() {
        assertThatThrownBy(() -> validatorStrict.validate("http://192.168.1.1/admin"))
                .isInstanceOf(UrlValidator.InvalidUrlException.class)
                .hasMessageContaining("Private");
    }

    @Test
    void urlWithNoHost_throwsInvalidUrlException() {
        assertThatThrownBy(() -> validatorStrict.validate("https://"))
                .isInstanceOf(UrlValidator.InvalidUrlException.class);
    }
}
