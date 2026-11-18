# Known Limitations

Current as of Phase 1 (2026-11-19).

## Providers
- **OpenAlex** needs a free API key in practice. Without `OPENALEX_API_KEY` it may return HTTP 429, which the UI reports as "could not be searched". Creating a key requires an account with a real email address, so it was not done on your behalf.
- **Project Gutenberg (Gutendex)** was unreachable from the build sandbox, so its adapter has never run against the live service.
- Provider trust status is `PENDING_REVIEW` for all five. Terms were only partly read (see PROVIDER_REGISTRY). Approval is a human decision.
- Search covers five open sources. It is not a search of the whole internet and the UI says so.

## Search
- A search waits for the slowest provider, up to `ABHIKNOW_PROVIDER_TIMEOUT_MS` (8 s). Results are not streamed yet.
- Filters are applied after the providers answer, so a filtered page can show fewer than a full page. Results without a year or language are hidden when filtering on them.
- Only the first page of each provider is exposed in the UI. The API accepts `page` (1 to 20).
- Query understanding only strips common filler phrases ("I want to become an expert in…"). It does not expand topics or detect prerequisites yet.
- Ranking uses simple documented weights. It has not been tuned against relevance judgements. For broad queries, arXiv preprints can dominate; the "recommended" shortlist limits this to two per type.
- Open Library `ebook_access` describes the work in general, not necessarily the edition you would open.
- No result is labelled "paid" because no provider returns a verified price yet.
- The language filter offers English only; most records carry no language.

## Not built yet
- Accounts, collections, bookmarks, notes, readers, progress (Phase 2).
- Downloads and human-verification handoffs (Phase 3). Links only open the provider's own site.
- AI features, learning paths, completeness dashboard, monitoring, collaboration.
- API rate limiting and authentication on this service's own endpoints. Do not expose it to the public internet yet.
- Internationalisation beyond Unicode-safe text handling. English UI only.

## Tooling
- Docker was not available in the build sandbox. `infrastructure/docker-compose.yml` (PostgreSQL only) has not been run. Local development used a native PostgreSQL 16.
- Backend uses Spring `JdbcClient` rather than Spring Data JPA. This keeps the SQL explicit; JPA can be added if it pays off.
- The frontend uses plain semantic HTML and Tailwind, not a component library. Accessibility was designed in (labels, landmarks, focus styles, text with icons for status) but not audited with a screen reader.
