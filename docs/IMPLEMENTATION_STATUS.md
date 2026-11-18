# Implementation Status

Status values: Not started · In progress · Implemented · Tested · Blocked · Deferred. No percentages are tracked.
"Tested" means automated tests pass. "Verified live" means it was also exercised against the real provider on 2026-11-19.

_Last updated: 2026-11-19_

## Phase 0: Foundation

| Item | Status | Notes |
|---|---|---|
| Repository assessment, requirements, architecture, data model, roadmap | Implemented | Documents |
| Licensing and provider registers | Implemented | Terms partly reviewed, see PROVIDER_REGISTRY |
| V1 migration | Tested | Applied cleanly to PostgreSQL 16 |
| Competitive analysis | Implemented | Draft from general knowledge; sources not cited |

## Phase 1: Discovery MVP

| Item | Status | Notes |
|---|---|---|
| Spring Boot 3.5 / Java 21 backend, Flyway migrations (V1, V2) | Tested | Migrations applied from a clean schema in the integration test |
| Provider runtime (timeout, retry with backoff, request spacing, circuit breaker, cache, failure isolation) | Tested | Unit tests with fake providers and a controllable clock |
| Crossref adapter | Verified live | Keyword, DOI, ISBN and author queries |
| arXiv adapter | Verified live | Keyword and author queries; 3 s request spacing; XML parsed with DOCTYPE disabled |
| Open Library adapter | Verified live | Keyword, ISBN and author queries |
| OpenAlex adapter | Tested (stub only) | Live call returned HTTP 429: the shared no-key budget was exhausted. Needs `OPENALEX_API_KEY`. Parsing is tested against a fixture written from the documented schema, not a real response |
| Project Gutenberg (Gutendex) adapter | Tested (stub only) | Host unreachable from the build sandbox (timeout). Parsing is tested against a hand-written fixture |
| Query parser (DOI, ISBN, arXiv id, author:, quoted phrases, filler removal) | Tested | Deterministic, no LLM |
| Normalization and deduplication (DOI, ISBN, arXiv id, fuzzy title+author) | Tested | Preprint vs published paper and book editions stay separate and are linked |
| Access classification from provider evidence | Tested | Unknown stays unknown; read/download/redistribute kept separate |
| Explainable ranking and recommended shortlist | Tested | Weights documented in `Ranker`; citation counts are not used |
| Persistence of results, provenance, search sessions, provider usage | Tested | Real PostgreSQL |
| REST API: `/api/search`, `/api/resources/{id}`, `/api/providers`, `/actuator/health` | Tested | MockMvc against real PostgreSQL |
| React frontend: search, grouped results, filters, source status, detail page, sources page | Tested | 15 Vitest tests; checked in Chromium against the live backend |
| Progressive (streamed) results | Not started | A search waits for the slowest provider (up to the 8 s timeout) |
| Playwright end-to-end tests in the repo | Not started | The browser check was done by hand |
| Docker images for backend and frontend, CI workflow | Not started | Docker is not available in the build sandbox, so nothing was verified |

Acceptance scenario (REQUIREMENTS section 32), Phase 1 items only:

| Item | Result |
|---|---|
| Query accepted: "Artificial Intelligence from beginner to advanced research." | Pass. Searched as "Artificial Intelligence" |
| Real results from multiple providers | Pass for Crossref, arXiv and Open Library (45 results). OpenAlex and Gutenberg did not answer, see above |
| Deduplication | Pass in tests and in the full-stack test (OpenAlex+arXiv preprint merged via the arXiv DOI). Not observed live because OpenAlex was rate limited |
| Free/paid classification | Partial. Free vs unknown vs restricted is classified from provider data. No provider asserted a verified price, so no result is labelled "paid" |
| Source attribution, provider failure shown | Pass |
| Clear access restrictions | Pass (restricted, borrowing, unknown shown with separate permissions) |
| Saving, reading PDF/EPUB, notes, bookmarks, progress | Not started (Phase 2) |

## Phase 2 onward

Not started.
