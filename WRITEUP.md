# Write-Up: URL Shortener Engineering Exercise

---

## 1. What did I ask the AI to do, and what did I write or decide myself?

I used the AI (Antigravity / Claude) as an accelerator for boilerplate and structure, while
owning every design decision myself before a line of code was written.

**What I decided myself — before any code was generated:**

- **Architecture.** A clean layered design: `Controller → Service → Repository`, with all business logic
  confined to the service layer. The controller does nothing but parse the HTTP request, call the service,
  and translate the result to an HTTP response.
- **Short-code algorithm.** Base62 (`[A-Za-z0-9]`) encoded from a `SecureRandom` long, truncated to
  7 characters. I explicitly rejected sequential IDs (enumerable, a privacy risk) and URL hashing (it
  hard-wires the duplicate-URL policy into the algorithm itself).
- **Duplicate-URL policy.** A configurable `app.duplicate-url-policy` property with two modes:
  `RETURN_EXISTING` (idempotent, the default) and `CREATE_NEW` (multi-code per URL). I chose to
  signal the distinction in the response with an `existing: true` flag rather than hiding it from the caller.
- **Custom alias failure mode.** A 409 Conflict when the alias is taken — never a silent fallback to
  an auto-generated code, because that would silently override the caller's stated intent.
- **Error format.** RFC 7807 `ProblemDetail` instead of a custom error envelope, because it is a
  standard that API clients already understand.
- **Hit-count increment strategy.** A single `@Modifying` JPQL `UPDATE … SET hit_count = hit_count + 1`
  rather than a read-modify-write on the entity — atomic at the database level.
- **Test strategy.** Unit tests with Mockito for the service, pure unit tests for the generator and validator,
  and full `@SpringBootTest` integration tests for the controller, backed by H2 in PostgreSQL compatibility mode.

**What the AI did:**

- Generated Java boilerplate (entity, repository, controller, exception handler, DTOs) from my specifications
  once the architecture was settled.
- Produced test scaffolding after I outlined the exact scenarios to cover.
- Formatted Javadoc and inline comments from my design rationale notes.

---

## 2. Where did I override, correct, or throw away the AI's output — and why?

**Override: H2 PostgreSQL compatibility mode in tests.**  
The AI initially suggested `spring.jpa.hibernate.ddl-auto=create-drop` for tests, which would bypass
Flyway entirely. I rejected this because tests would never exercise the actual migration script — a whole
class of production bugs (wrong column type, missing index, constraint name collision) would go undetected.
I switched to `H2 MODE=PostgreSQL` with Flyway enabled in the test profile so the migration runs on every
test execution.

**Override: `@Transactional` isolation in integration tests.**  
The AI wrote integration tests without rollback annotations, causing inter-test state pollution: a custom alias
created in one test would trigger a 409 in the next. I added `@Transactional` at the class level, which rolls
back each test method independently.

**Override: Hit-count increment.**  
The AI initially implemented `resolve()` by loading the entity, calling `setHitCount(n + 1)`, and saving it.
Under concurrent load that is a read-modify-write race — two simultaneous redirects could both read `n`,
both write `n + 1`, and one increment would be lost. I replaced it with the single-statement JPQL update,
which lets the database handle the atomicity.

**Thrown away: Testcontainers.**  
The AI suggested Testcontainers for integration tests (a real PostgreSQL container per test run). I decided
against it: it requires Docker in CI, adds cold-start latency, and the Flyway migration uses only standard SQL
that H2 in PostgreSQL mode handles correctly. H2 wins on portability for this exercise. A production-grade
service would layer Testcontainers on top in a separate CI stage.

---

## 3. The two or three biggest trade-offs I made

**Trade-off 1: Idempotent shortening (`RETURN_EXISTING`) vs. always creating new codes**

*Chosen:* Return the existing mapping when the same URL is shortened again, and signal this with
`existing: true` in the response.  
*Alternative:* Always generate a fresh code — useful for per-campaign tracking where you want separate
analytics per link even for the same target.  
*Why idempotent:* It is the most predictable behaviour for a generic shortener. Repeated POSTs don't
silently bloat the code space. Callers that need the other behaviour can flip one config property
(`CREATE_NEW`); both paths are tested.

**Trade-off 2: 301 Permanent vs. 302 Temporary redirect**

*Chosen:* 301 — semantically correct for a shortener where links are permanent. Browsers and CDNs
cache it, reducing future load on this service.  
*Alternative:* 302 — fully dynamic; clients always re-fetch, so the target can be changed invisibly.  
*Why 301:* The shortener's core value proposition is a stable, permanent link. The cache benefit is
material. The downside — cached 301s don't update if the target changes — is acceptable here and
would be mitigated in production with a `Cache-Control: max-age` header.

**Trade-off 3: H2 for tests vs. Testcontainers + real PostgreSQL**

*Chosen:* H2 in PostgreSQL compatibility mode — zero infrastructure, instant startup, runs anywhere
without Docker.  
*Alternative:* Testcontainers with a real PostgreSQL 16 image — highest possible fidelity, catches
PostgreSQL-specific behaviour (e.g. `TIMESTAMPTZ` precision, advisory locks) that H2 might diverge on.  
*Why H2:* The migration uses only standard SQL; H2's PostgreSQL mode handles it correctly. For a
take-home exercise where the reviewer needs to run tests without a daemon, H2 is the right choice.
Testcontainers would be the first addition in a real CI pipeline.

---

## 4. What's missing, or what I'd do with another day?

**Link expiry enforcement.**  
The `expires_at` column exists in the schema and is surfaced in `StatsResponse`, but `resolve()` does
not yet check it. A one-line guard in the service (`if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isBefore(Instant.now()))`) plus a `POST /shorten` field to accept a TTL would complete this.

**Rate limiting.**  
`POST /shorten` is wide open. Production would need per-IP or per-API-key rate limiting (Spring Security +
Bucket4j, or a gateway-level policy) to prevent code-space exhaustion and abuse.

**Authentication & link management.**  
`DELETE /{code}` and `PATCH /shorten/{code}` for updating the target URL, protected behind
authentication. Without ownership tracking, any caller can redirect or delete any link.

**Testcontainers in CI.**  
A second Maven profile running the integration tests against real PostgreSQL 16 via Testcontainers
would give full fidelity and catch any gap between H2 PostgreSQL mode and the real engine.

**Metrics & observability.**  
Micrometer + a Prometheus scrape endpoint to expose redirect throughput, p99 latency, and code-space
usage. The `hit_count` column is already the raw material for a redirect-rate dashboard.

**URL reachability check.**  
An optional HTTP `HEAD` request (configurable timeout, disabled by default) to verify the target is
reachable before issuing a code — avoids shortening dead links.
