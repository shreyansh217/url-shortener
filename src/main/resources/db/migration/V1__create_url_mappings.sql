-- V1: Create the core url_mappings table
-- This is the single source of truth for all short-code → original-URL mappings.
--
-- Design notes:
--   * short_code is UNIQUE – enforced at DB level as a safety net beyond app-level checks.
--   * original_url uses TEXT (unbounded) to accommodate URLs up to 2048 chars validated at app layer.
--   * hit_count is updated in-place; good enough for analytics at this scale.
--   * expires_at is nullable – NULL means "never expires".
--   * is_custom distinguishes auto-generated codes from user-supplied aliases.

CREATE TABLE url_mappings (
    id           BIGSERIAL    PRIMARY KEY,
    short_code   VARCHAR(16)  NOT NULL,
    original_url TEXT         NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMP WITH TIME ZONE,
    hit_count    BIGINT       NOT NULL DEFAULT 0,
    is_custom    BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_short_code UNIQUE (short_code)
);

-- Fast lookup by short code (primary query path: GET /{code})
CREATE INDEX idx_short_code ON url_mappings (short_code);

-- Used for duplicate-URL detection (POST /shorten idempotency check)
CREATE INDEX idx_original_url ON url_mappings (original_url);
