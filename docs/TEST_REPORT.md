# Test Report

_Run on 2026-11-19, Phase 1._

| Suite | Command | Result |
|---|---|---|
| Backend unit + integration | `mvn test` (with `ABHIKNOW_TEST_DB_URL` set) | 75 run, 0 failed, 0 skipped |
| Frontend | `npm test` | 15 passed in 2 files; `npm run typecheck` clean; `npm run build` succeeds |

Without `ABHIKNOW_TEST_DB_URL` the 6 database tests are skipped (69 run).

## What the backend tests cover
- Query parsing: DOI, ISBN-10/13 with checksum, arXiv ids, author queries, quoted phrases, the acceptance query, blank input.
- Identifier and text normalization; URL scheme allow-list (`javascript:` and `data:` are dropped).
- Access classification for each provider, including that unknown is never treated as free.
- Deduplication: same DOI/ISBN, fuzzy match, different authors, missing authors, editions, preprint vs published paper, arXiv DOI merge.
- Ranking: title match, corroboration, citations not affecting score, books not penalised for age, bounded and deterministic scores.
- Provider runtime: cache, retry with backoff, no retry on 429, timeout, isolation of unexpected exceptions, circuit breaker open/recover, request-spacing limit.
- Adapters: mapping from real captured payloads (Crossref, arXiv, Open Library) and hand-written fixtures (OpenAlex, Gutendex, plus extra Crossref cases); query translation, User-Agent with contact email, API key only when configured; HTTP 400/404/429/503, malformed JSON, connection refused; XML with a DOCTYPE is rejected.
- Search service: one provider failing and one timing out do not stop the search; blocked and unregistered providers are never queried; skipped providers; filters; recommended shortlist.
- Full stack on PostgreSQL: migrations from a clean schema, real adapters against local stub servers, merged results, idempotent upserts, detail endpoint, usage and session recording, input validation, 404s, blocked-provider handling.

## Defects found and fixed during this phase
1. Spring could not pick a constructor for `ProviderRuntime` (found by the full-stack test).
2. PostgreSQL rejected the JVM time zone `Asia/Calcutta`; the app and tests now run in UTC.
3. Fuzzy matching merged a preprint with its published version; types must now match to merge.
4. Crossref single-DOI lookup returned HTTP 400 because that route rejects `select`, and the adapter hid the error as "not found". Found only by a live call. Fixed, and 404 is now the only status treated as "unknown DOI". Regression tests added.

## Not covered
Playwright end-to-end tests, accessibility audit with assistive technology, performance and load tests, and live tests of OpenAlex and Gutendex.
