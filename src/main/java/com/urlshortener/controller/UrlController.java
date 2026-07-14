package com.urlshortener.controller;

import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.dto.StatsResponse;
import com.urlshortener.service.UrlShortenerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * REST controller exposing the URL shortener API.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code POST /shorten}        – Create a short code for a given URL</li>
 *   <li>{@code GET  /{code}}          – Redirect (301) to the original URL</li>
 *   <li>{@code GET  /api/stats/{code}} – Retrieve analytics for a short code</li>
 * </ul>
 *
 * <h2>Why 301 and not 302?</h2>
 * 301 (Moved Permanently) is semantically correct for a URL shortener — the mapping is
 * intended to be permanent. Browsers and CDNs cache 301s, reducing load on this service
 * over time. A 302 (Found/Temporary) would defeat that caching benefit.
 * Trade-off: if you need to change the target URL later, cached 301s in browsers won't
 * update automatically. Acceptable for this exercise; production would add TTL/cache-control.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class UrlController {

    private final UrlShortenerService service;

    /**
     * Shorten a URL.
     *
     * @param request JSON body containing {@code url} and optional {@code customAlias}
     * @return 200 with {@link ShortenResponse}; 400 for invalid URL; 409 for alias conflict
     */
    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        ShortenResponse response = service.shorten(request);
        log.info("POST /shorten → {} (existing={})", response.shortCode(), response.existing());
        return ResponseEntity.ok(response);
    }

    /**
     * Redirect to original URL.
     *
     * @param code the short code
     * @return 301 redirect; 404 if code not found
     */
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String originalUrl = service.resolve(code);
        log.info("GET /{} → 301 {}", code, originalUrl);
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, originalUrl)
                .build();
    }

    /**
     * Get analytics stats for a short code.
     *
     * @param code the short code
     * @return 200 with {@link StatsResponse}; 404 if code not found
     */
    @GetMapping("/api/stats/{code}")
    public ResponseEntity<StatsResponse> stats(@PathVariable String code) {
        StatsResponse response = service.getStats(code);
        return ResponseEntity.ok(response);
    }
}
