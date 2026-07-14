package com.urlshortener.model;

import jakarta.persistence.*;
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
    private long hitCount = 0L;

    @Column(name = "is_custom", nullable = false)
    private boolean isCustom = false;

    // ── Constructors ──────────────────────────────────────────────────────────

    public UrlMapping() {}

    private UrlMapping(Builder builder) {
        this.id          = builder.id;
        this.shortCode   = builder.shortCode;
        this.originalUrl = builder.originalUrl;
        this.createdAt   = builder.createdAt;
        this.expiresAt   = builder.expiresAt;
        this.hitCount    = builder.hitCount;
        this.isCustom    = builder.isCustom;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long id;
        private String shortCode;
        private String originalUrl;
        private Instant createdAt;
        private Instant expiresAt;
        private long hitCount = 0L;
        private boolean isCustom = false;

        public Builder id(Long id)                   { this.id = id; return this; }
        public Builder shortCode(String shortCode)   { this.shortCode = shortCode; return this; }
        public Builder originalUrl(String url)       { this.originalUrl = url; return this; }
        public Builder createdAt(Instant createdAt)  { this.createdAt = createdAt; return this; }
        public Builder expiresAt(Instant expiresAt)  { this.expiresAt = expiresAt; return this; }
        public Builder hitCount(long hitCount)       { this.hitCount = hitCount; return this; }
        public Builder isCustom(boolean isCustom)    { this.isCustom = isCustom; return this; }

        public UrlMapping build() {
            return new UrlMapping(this);
        }
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long getId()                        { return id; }
    public void setId(Long id)                 { this.id = id; }

    public String getShortCode()               { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }

    public String getOriginalUrl()             { return originalUrl; }
    public void setOriginalUrl(String url)     { this.originalUrl = url; }

    public Instant getCreatedAt()              { return createdAt; }
    public void setCreatedAt(Instant createdAt){ this.createdAt = createdAt; }

    public Instant getExpiresAt()              { return expiresAt; }
    public void setExpiresAt(Instant expiresAt){ this.expiresAt = expiresAt; }

    public long getHitCount()                  { return hitCount; }
    public void setHitCount(long hitCount)     { this.hitCount = hitCount; }

    public boolean isCustom()                  { return isCustom; }
    public void setCustom(boolean isCustom)    { this.isCustom = isCustom; }
}
