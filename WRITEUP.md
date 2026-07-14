# Write-Up — URL Shortener

## 1. What I asked the AI to do vs. what I decided myself

I made all the design calls upfront, then used the AI to fill in boilerplate.

The decisions were mine: layered architecture (controller talks only to service, service talks only to repo), Base62 + `SecureRandom` for codes (not sequential IDs — those are guessable; not URL hashes — those lock in the duplicate policy), configurable `RETURN_EXISTING` / `CREATE_NEW` duplicate behaviour with an `existing` flag in the response, 409 on alias conflict with no silent fallback, RFC 7807 `ProblemDetail` for errors, and an atomic JPQL `UPDATE hit_count = hit_count + 1` instead of a load-modify-save.

The AI generated the Java boilerplate — entity, repository, DTOs, exception handler — once the shape was clear. It also wrote first-draft tests after I told it which scenarios to cover, and formatted Javadoc from notes I'd written.

## 2. Where I overrode or threw away the AI's output

**Tests used `ddl-auto=create-drop`, skipping Flyway.** I changed it to H2 in PostgreSQL compatibility mode with Flyway enabled. If tests never run the migration, you'll miss schema bugs that only show up in production.

**Integration tests had no rollback.** State from one test bled into the next — a custom alias created in test A caused a 409 in test B. I added `@Transactional` at the class level.

**Hit-count was a read-modify-write.** Two concurrent redirects could both read `n` and both write `n + 1`, losing an increment. Replaced with the single-statement update.

**Threw away Testcontainers.** The AI suggested it. The Flyway migration is plain SQL that H2 handles fine, and Testcontainers needs Docker in CI. H2 is simpler and more portable for a take-home.

## 3. The biggest trade-offs

**Idempotent shortening.** `RETURN_EXISTING` means the same URL always returns the same code, flagged with `existing: true`. The alternative — always creating a new code — is better for per-campaign analytics but wastes code space for normal use. Idempotent is the safer default; `CREATE_NEW` is one config line away.

**301 vs. 302.** I went with 301 because it's semantically right for permanent links and browsers cache it, reducing load over time. The downside is that a cached 301 won't update if the target changes. Acceptable here; in production I'd add `Cache-Control: max-age` to bound the cache TTL.

**H2 vs. Testcontainers.** H2 runs anywhere instantly. Testcontainers gives real PostgreSQL fidelity but needs Docker. For a reviewer who should be able to run `mvn test` cold, H2 wins.

## 4. What's missing

- `expires_at` exists in the schema but `resolve()` doesn't enforce it yet — one null-check away.
- No rate limiting on `POST /shorten` — easy abuse vector.
- No auth, so there's no ownership model and no `DELETE` or `PATCH` endpoints.
- Testcontainers in a separate CI profile for full PostgreSQL fidelity.
- Micrometer metrics for redirect throughput and latency.
