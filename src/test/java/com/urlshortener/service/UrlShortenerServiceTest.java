package com.urlshortener.service;

import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.exception.AliasAlreadyTakenException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.repository.UrlMappingRepository;
import com.urlshortener.validation.UrlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UrlShortenerService} using Mockito.
 * All dependencies are mocked — no Spring context, no DB.
 */
@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    @Mock private UrlMappingRepository repository;
    @Mock private ShortCodeGenerator generator;
    @Mock private UrlValidator urlValidator;

    @InjectMocks
    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(service, "duplicateUrlPolicy", "RETURN_EXISTING");
    }

    // ── POST /shorten – happy paths ───────────────────────────────────────────

    @Test
    void shorten_newUrl_createsAndReturnsMapping() {
        String url = "https://example.com/long-path";
        String code = "abc1234";

        when(generator.generate()).thenReturn(code);
        when(repository.findFirstByOriginalUrl(url)).thenReturn(Optional.empty());
        when(repository.existsByShortCode(code)).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> {
            UrlMapping m = inv.getArgument(0);
            m = UrlMapping.builder()
                    .id(1L).shortCode(m.getShortCode())
                    .originalUrl(m.getOriginalUrl())
                    .createdAt(Instant.now()).build();
            return m;
        });

        ShortenResponse response = service.shorten(new ShortenRequest(url, null));

        assertThat(response.shortCode()).isEqualTo(code);
        assertThat(response.originalUrl()).isEqualTo(url);
        assertThat(response.existing()).isFalse();
        assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/" + code);
    }

    @Test
    void shorten_duplicateUrl_returnExistingMapping_whenPolicyIsReturnExisting() {
        String url = "https://example.com/already-shortened";
        UrlMapping existing = UrlMapping.builder()
                .id(1L).shortCode("existXX").originalUrl(url).createdAt(Instant.now()).build();

        when(repository.findFirstByOriginalUrl(url)).thenReturn(Optional.of(existing));

        ShortenResponse response = service.shorten(new ShortenRequest(url, null));

        assertThat(response.shortCode()).isEqualTo("existXX");
        assertThat(response.existing()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    void shorten_withCustomAlias_createsCustomMapping() {
        String url = "https://example.com";
        String alias = "my-alias";

        when(generator.isValidAlias(alias)).thenReturn(true);
        when(repository.existsByShortCode(alias)).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> {
            UrlMapping m = inv.getArgument(0);
            return UrlMapping.builder()
                    .id(2L).shortCode(m.getShortCode())
                    .originalUrl(m.getOriginalUrl())
                    .isCustom(true).createdAt(Instant.now()).build();
        });

        ShortenResponse response = service.shorten(new ShortenRequest(url, alias));

        assertThat(response.shortCode()).isEqualTo(alias);
        assertThat(response.existing()).isFalse();
    }

    @Test
    void shorten_withCustomAlias_takenAlias_throws409() {
        when(generator.isValidAlias("taken")).thenReturn(true);
        when(repository.existsByShortCode("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.shorten(new ShortenRequest("https://example.com", "taken")))
                .isInstanceOf(AliasAlreadyTakenException.class)
                .hasMessageContaining("taken");
    }

    @Test
    void shorten_withInvalidCustomAlias_throws400() {
        when(generator.isValidAlias("bad alias!")).thenReturn(false);

        assertThatThrownBy(() -> service.shorten(new ShortenRequest("https://example.com", "bad alias!")))
                .isInstanceOf(UrlValidator.InvalidUrlException.class);
    }

    // ── GET /{code} ───────────────────────────────────────────────────────────

    @Test
    void resolve_existingCode_returnsOriginalUrl() {
        String code = "abc1234";
        String url = "https://example.com";
        UrlMapping mapping = UrlMapping.builder()
                .shortCode(code).originalUrl(url).hitCount(0).createdAt(Instant.now()).build();

        when(repository.findByShortCode(code)).thenReturn(Optional.of(mapping));

        String resolved = service.resolve(code);

        assertThat(resolved).isEqualTo(url);
        verify(repository).incrementHitCount(code);
    }

    @Test
    void resolve_unknownCode_throws404() {
        when(repository.findByShortCode("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("unknown"))
                .isInstanceOf(UrlNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    // ── GET /api/stats/{code} ─────────────────────────────────────────────────

    @Test
    void getStats_existingCode_returnsStats() {
        String code = "abc1234";
        UrlMapping mapping = UrlMapping.builder()
                .shortCode(code).originalUrl("https://example.com")
                .hitCount(42L).isCustom(false).createdAt(Instant.now()).build();

        when(repository.findByShortCode(code)).thenReturn(Optional.of(mapping));

        var stats = service.getStats(code);

        assertThat(stats.hitCount()).isEqualTo(42L);
        assertThat(stats.shortCode()).isEqualTo(code);
    }

    @Test
    void getStats_unknownCode_throws404() {
        when(repository.findByShortCode("xyz")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStats("xyz"))
                .isInstanceOf(UrlNotFoundException.class);
    }
}
