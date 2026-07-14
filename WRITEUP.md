# Write-Up: URL Shortener Engineering Exercise

---

## 1. What did I ask the AI to do, and what did I write or decide myself?

I used the AI (Antigravity / Claude) as an accelerator for boilerplate and structure,
while owning all design decisions myself.

**What the AI did:**
- Generated boilerplate Java files (entity, repository, controller, exception handler)
  from my specifications once I had decided on the architecture
- Produced test scaffolding after I outlined the scenarios I wanted covered
- Formatted Javadoc and comments from my written design rationale

**What I decided myself (before any code was written):**
- The overall layered architecture (controller → service → repository), keeping business
  logic entirely out of the controller
- The short-code algorithm: Base62 + `SecureRandom` with a collision retry loop. I explicitly
  rejected sequential IDs (enumerable) and URL hashing (breaks policy flexibility)
- The duplicate-URL policy: `RETURN_EXISTING` as the default with `CREATE_NEW` as a
  switchable alternative. I chose this because most callers expect idempotent shortening
- Using RFC 7807 `ProblemDetail` for error responses rather than a custom error envelope
- The `@Modifying` JPQL query for `incrementHitCount` — avoiding a full entity load on every redirect
- The test strategy: unit tests with Mockito for the service, pure unit tests for the
  generator and validator, and full `@SpringBootTest` integration tests for the controller
  using H2 in PostgreSQL compatibility mode

---

## 2. Where did I override, correct, or throw away the AI's output — and why?

**Override: H2 PostgreSQL compatibility mode in tests**
The AI initially suggested `spring.jpa.hibernate.ddl-auto=create-drop` for tests, which
would bypass Flyway entirely. I rejected this because it means tests never run against the
real migration script — a class of production bug would never be caught. I switched to
`H2 MODE=PostgreSQL` with Flyway enabled in the test profile.

**Override: `@Transactional` in integration tests**
The AI wrote integration tests that did not roll back between test methods, causing
inter-test pollution (custom alias created in one test would conflict with the next).
I added `@Transactional` at the class level with the default rollback semantics.

**Override: Hit-count increment strategy**
The AI initially wrote `resolve()` to load the entity, call `setHitCount(n + 1)`, and save it.
This is a read-modify-write that races under concurrent load. I replaced it with a single
`@Modifying` JPQL `UPDATE ... SET hit_count = hit_count + 1` which is atomic at the DB level.

**Thrown away: Testcontainers**
The AI suggested Testcontainers for integration tests. I decided against it because it
requires Docker in CI, adds cold-start time, and H2 in PostgreSQL compatibility mode is
sufficient for testing the Flyway migration and query logic without the infrastructure overhead.

---

## 3. The two or three biggest trade-offs I made

**Trade-off 1: Idempotent shortening (RETURN_EXISTING) vs. always creating new codes**

*Chosen:* Return the existing mapping when the same URL is shortened again.
*Alternative:* Always create a new code (useful for per-campaign tracking, where you want
different analytics per link even if the target is the same).
*Why I chose idempotent:* It's the most predictable behaviour for a generic shortener. Repeated
POSTs with the same URL don't pollute the code space. The `existing: true` flag in the response
makes the behaviour explicit. `CREATE_NEW` is one config property away.

**Trade-off 2: 301 Permanent vs. 302 Temporary redirect**

*Chosen:* 301 — semantically correct for permanent mappings; browsers and CDNs cache it,
reducing load on the service over time.
*Alternative:* 302 — fully flexible; redirects can be changed and clients always re-fetch.
*Why 301:* For a shortener, links are intended to be stable. The cache benefit outweighs the
flexibility cost. In production I'd add a `Cache-Control: max-age` header to bound the TTL.

**Trade-off 3: H2 for tests vs. Testcontainers + real PostgreSQL**

*Chosen:* H2 in PostgreSQL compatibility mode — zero infrastructure, fast, runs anywhere.
*Alternative:* Testcontainers — real PostgreSQL, highest fidelity.
*Why H2:* The Flyway migration uses only standard SQL (no PostgreSQL-specific types beyond
`TIMESTAMPTZ`, which H2 handles in PostgreSQL mode). For this exercise, the simplicity and
portability of H2 wins. A production service would use Testcontainers in CI.

---

## 4. What's missing, or what I'd do with another day?

- **Link expiry:** The `expires_at` column exists in the schema but the service doesn't
  enforce it on redirect. A scheduled job (Spring `@Scheduled`) or a DB-level check in
  `resolve()` would complete this.
- **Rate limiting:** `POST /shorten` is wide open. In production, add Spring Security +
  bucket4j or API keys to prevent abuse.
- **Testcontainers in CI:** Replace H2 with a real PostgreSQL container for the highest-
  fidelity integration test suite.
- **URL reachability check:** Optionally verify the target URL is reachable before shortening
  (with a short HTTP HEAD request and configurable timeout).
- **Delete / update endpoints:** `DELETE /{code}` and `PATCH /shorten/{code}` for link
  management, protected behind authentication.
- **Metrics & observability:** Micrometer + Prometheus scrape endpoint for hit-rate dashboards.
