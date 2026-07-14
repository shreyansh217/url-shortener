package com.urlshortener.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity representing a single short-code → original-URL mapping.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code shortCode}   – URL-safe Base62 string (7 chars auto-generated, up to 16 for custom aliases)</li>
 *   <li>{@code originalUrl} – The full URL the short code redirects to</li>
 *   <li>{@code hitCount}    – Cumulative redirect count (analytics)</li>
 *   <li>{@code isCustom}    – True when the alias was supplied by the caller, not auto-generated</li>
 *   <li>{@code expiresAt}   – Optional TTL; null means "never expires"</li>
 * </ul>
 */
@Entity
@Table(name = "url_mappings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, unique = true, length = 16)
    private String shortCode;

    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "hit_count", nullable = false)
    @Builder.Default
    private long hitCount = 0L;

    @Column(name = "is_custom", nullable = false)
    @Builder.Default
    private boolean isCustom = false;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
