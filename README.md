# URL Shortener & Link Analytics

A production-quality URL shortener service built with **Java 21 · Spring Boot 3.3 · PostgreSQL**.

[![Tests](https://img.shields.io/badge/tests-passing-brightgreen)](#running-tests)
[![Java](https://img.shields.io/badge/Java-21-blue)](#prerequisites)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-green)](#prerequisites)

---

## Features

| Feature | Detail |
|---|---|
| `POST /shorten` | Shorten any valid HTTP/HTTPS URL |
| `GET /{code}` | 301 redirect to the original URL |
| `GET /api/stats/{code}` | Analytics: hit count, created date, custom flag, expiry |
| Custom aliases | Supply your own memorable short code (`[A-Za-z0-9_-]`, max 16 chars) |
| Duplicate URL policy | Configurable: return existing code or always create a new one |
| Collision-resistant codes | Base62 + `SecureRandom` — ~3.5 trillion code-space |
| RFC 7807 errors | Machine-readable `ProblemDetail` JSON error responses |
| Flyway migrations | Version-controlled schema, applied on startup |
| Link expiry schema | `expires_at` column ready for future enforcement |

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 21 or higher |
| Maven | 3.8+ |
| Docker & Docker Compose | Any recent version (for PostgreSQL) |

> **No Docker?** Tests run without Docker — see [Running Tests](#running-tests).  
> For local development without Docker, install [PostgreSQL 16](https://www.postgresql.org/download/) locally and create a database named `urlshortener`.

---

## Quick Start

### 1. Clone the repository

```bash
git clone https://github.com/shreyansh217/url-shortener.git
cd url-shortener
```

### 2. Start PostgreSQL via Docker

```bash
docker-compose up -d
```

Wait for the health check to pass (≈5–10 seconds):

```bash
docker-compose ps   # Status column should show "healthy"
```

The container uses these defaults (matching `application.properties`):

| Setting | Value |
|---|---|
| Host | `localhost:5432` |
| Database | `urlshortener` |
| Username | `postgres` |
| Password | `postgres` |

### 3. Run the application

```bash
mvn spring-boot:run
```

Flyway runs automatically on startup and applies `V1__create_url_mappings.sql`.  
The service is ready at **`http://localhost:8080`**.

---

## Running Tests

Tests use an **H2 in-memory database in PostgreSQL compatibility mode** — no Docker or running
database is needed:

```bash
mvn test
```

The test suite covers:

| Test class | What it tests |
|---|---|
| `UrlControllerIntegrationTest` | Full HTTP round-trips via `MockMvc` + H2 |
| `UrlShortenerServiceTest` | Business logic, duplicate policy, alias conflict, collision retry |
| `ShortCodeGeneratorTest` | Code length, character set, uniqueness across 10 000 calls |
| `UrlValidatorTest` | Valid/invalid schemes, private IPs, length limits |

---

## API Reference

### `POST /shorten` — Shorten a URL

```bash
# Basic shorten
curl -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.example.com/very/long/path"}'
```

```bash
# With a custom alias
curl -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.example.com", "customAlias": "my-link"}'
```

**Request body:**

```json
{
  "url": "https://www.example.com/very/long/path",
  "customAlias": "my-link"   // optional — omit for auto-generated code
}
```

**Response `200 OK`:**

```json
{
  "shortCode": "aB3kR9z",
  "shortUrl":  "http://localhost:8080/aB3kR9z",
  "originalUrl": "https://www.example.com/very/long/path",
  "existing": false,
  "createdAt": "2025-01-01T00:00:00Z"
}
```

> **`existing: true`** means the URL was already shortened — the existing code is returned.  
> This behaviour is controlled by `app.duplicate-url-policy` (see [Configuration](#configuration)).

| Status | Meaning |
|---|---|
| `200` | Success (new code created, or existing code returned) |
| `400` | Invalid URL or alias contains illegal characters |
| `409` | Custom alias is already taken |

---

### `GET /{code}` — Redirect

```bash
curl -L http://localhost:8080/aB3kR9z
# Follows the 301 redirect to the original URL
```

| Status | Meaning |
|---|---|
| `301 Moved Permanently` | Redirect to original URL |
| `404 Not Found` | Unknown short code |

---

### `GET /api/stats/{code}` — Analytics

```bash
curl http://localhost:8080/api/stats/aB3kR9z
```

**Response `200 OK`:**

```json
{
  "shortCode": "aB3kR9z",
  "shortUrl": "http://localhost:8080/aB3kR9z",
  "originalUrl": "https://www.example.com/very/long/path",
  "hitCount": 5,
  "isCustom": false,
  "createdAt": "2025-01-01T00:00:00Z",
  "expiresAt": null
}
```

| Status | Meaning |
|---|---|
| `200` | Stats returned |
| `404` | Unknown short code |

---

## Error Response Format (RFC 7807)

All errors follow the standard `ProblemDetail` format:

```json
{
  "type": "about:blank",
  "title": "Short Code Not Found",
  "status": 404,
  "detail": "No short code found: 'abc123'",
  "instance": "/abc123"
}
```

---

## Configuration

Edit `src/main/resources/application.properties`:

| Property | Default | Description |
|---|---|---|
| `app.base-url` | `http://localhost:8080` | Prefix used when building the `shortUrl` in responses |
| `app.duplicate-url-policy` | `RETURN_EXISTING` | `RETURN_EXISTING` — return same code for duplicate URLs; `CREATE_NEW` — always generate fresh |
| `app.short-code-length` | `7` | Length of auto-generated codes (4–16) |
| `app.allow-localhost-urls` | `false` | Set `true` to allow `localhost` and RFC-1918 URLs (useful for local dev/testing) |

---

## Design Decisions

See [`WRITEUP.md`](WRITEUP.md) for the full one-page write-up covering AI usage, overrides, trade-offs, and what's missing.

### Short Code Algorithm

- `SecureRandom` → Base62 alphabet `[A-Za-z0-9]` → take first `N` characters (default 7)
- Code-space: **62⁷ ≈ 3.52 trillion** unique codes
- By the birthday paradox, collision probability reaches ~0.08% only after **~2.4 million codes** — orders of magnitude beyond realistic load
- Not guessable (unlike sequential IDs); not URL-dependent (unlike URL hashing, which locks in behaviour regardless of policy)
- Rare collisions handled by a **retry loop** (up to 5 attempts) in `UrlShortenerService`

### Duplicate URL Policy

- **`RETURN_EXISTING`** (default): idempotent — the same URL always returns the same short code. `existing: true` signals this to the caller.
- **`CREATE_NEW`**: always generates a fresh code — useful for per-campaign or per-source tracking.

### 301 vs 302

- **301 Permanent** is semantically correct for a URL shortener where mappings are stable. Browsers and CDNs cache it, reducing load over time.
- Trade-off: cached 301s won't update if the target URL changes later. Acceptable here; production would add a `Cache-Control: max-age` bound.

### Custom Alias Behaviour

- Aliases bypass the generator entirely — used verbatim as the short code.
- If a requested alias is already taken, the service returns **409 Conflict** immediately. It does *not* silently fall back to an auto-generated code, because that would ignore the caller's stated intent.

### Hit Count Increment

- Uses a single `@Modifying` JPQL `UPDATE … SET hit_count = hit_count + 1` — atomic at the database level, avoids a read-modify-write race under concurrent load.

---

## Project Structure

```
src/
├── main/
│   ├── java/com/urlshortener/
│   │   ├── controller/     UrlController.java
│   │   ├── service/        UrlShortenerService.java · ShortCodeGenerator.java · CodeGenerator.java
│   │   ├── model/          UrlMapping.java
│   │   ├── repository/     UrlMappingRepository.java
│   │   ├── dto/            ShortenRequest · ShortenResponse · StatsResponse
│   │   ├── exception/      GlobalExceptionHandler · UrlNotFoundException · AliasAlreadyTakenException
│   │   └── validation/     UrlValidator.java
│   └── resources/
│       ├── application.properties
│       └── db/migration/   V1__create_url_mappings.sql
└── test/
    ├── java/com/urlshortener/
    │   ├── controller/     UrlControllerIntegrationTest.java
    │   ├── service/        UrlShortenerServiceTest.java · ShortCodeGeneratorTest.java
    │   └── validation/     UrlValidatorTest.java
    └── resources/
        ├── application-test.properties
        └── mockito-extensions/org.mockito.plugins.MockMaker
```

---

## Database Schema

```sql
CREATE TABLE url_mappings (
    id           BIGSERIAL    PRIMARY KEY,
    short_code   VARCHAR(16)  NOT NULL,
    original_url TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMPTZ,
    hit_count    BIGINT       NOT NULL DEFAULT 0,
    is_custom    BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_short_code UNIQUE (short_code)
);

CREATE INDEX idx_short_code   ON url_mappings (short_code);    -- primary lookup path
CREATE INDEX idx_original_url ON url_mappings (original_url);  -- duplicate-URL detection
```

---

## Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Database | PostgreSQL 16 |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Test DB | H2 (PostgreSQL compatibility mode) |
| Testing | JUnit 5 · Mockito · MockMvc · AssertJ |
| Build | Maven 3.8+ |
