# Architecture

## Style

A **modular monolith** (Spring Boot) plus optional Python workers. Components are separated by package boundaries so they can be extracted later. No Kubernetes, no message broker, no graph DB in the MVP.

```
React SPA ──REST──▶ Spring Boot
                     ├─ search       orchestrates a query across providers
                     ├─ providers    one adapter per source (OpenAlex, Crossref, arXiv, Open Library, Gutenberg)
                     ├─ resource     canonical model, normalization, dedup
                     ├─ access       access classification (later: state machine, downloads)
                     ├─ ranking      explainable scoring
                     ├─ library      collections, notes (Phase 2)
                     └─ security     auth, rate limits, audit
                              │
                         PostgreSQL (+ pgvector later)      Local filesystem / S3-compatible store
```

## Search flow (Phase 1)

1. Parse query (detect DOI, ISBN, quoted phrases).
2. Fan out to enabled providers with bounded concurrency and a per-provider timeout.
3. Each adapter maps provider JSON/XML into `ProviderRecord`, including raw identifiers and provenance.
4. Normalizer converts records to canonical `Resource` candidates.
5. Deduplicator merges candidates by DOI → ISBN → canonical URL → fuzzy title+author, keeping every provider reference and preserving distinct editions/versions.
6. Access classifier assigns an access type only from provider-asserted evidence (license, open-access flag, Gutenberg public-domain, etc.); otherwise `UNKNOWN`.
7. Ranker scores relevance, accessibility, recency, source diversity; stores the reasons.
8. Response includes per-provider status (ok / timeout / error / rate limited) so the UI can be honest about coverage.

## Provider adapter contract

`search(query, page)`, rate limiting, retry with backoff, timeout, caching, normalization, health, usage counting, provenance. Failures are isolated and reported, never thrown to the caller.

## Data

PostgreSQL with Flyway migrations. Large files live outside the DB (filesystem now, S3-compatible interface later). See [DATA_MODEL](DATA_MODEL.md).

## Frontend

React, TypeScript, Vite, Tailwind, TanStack Query, React Router. Semantic, accessible components; a single prominent search field on the home page.

## Decisions

| Decision | Rationale |
|---|---|
| Modular monolith | Lowest operational cost; clear seams for later extraction |
| PostgreSQL FTS before vector search | Good enough for the MVP; pgvector added with the AI phase |
| Deterministic services first | Ranking, dedup and access classification are rule-based and testable |
| Provider trust registry | Only approved providers are queried or downloaded from |
