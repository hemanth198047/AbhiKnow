# Requirements

Priority: **M** = Must (MVP), **S** = Should, **C** = Could (later phase). Phase refers to [ROADMAP](ROADMAP.md).

## Functional

| ID | Requirement | Pri | Phase |
|---|---|---|---|
| F-01 | One search box accepting natural language, keywords, DOI, ISBN, author | M | 1 |
| F-02 | Parallel search across providers; one provider failing must not fail the search | M | 1 |
| F-03 | Results carry provenance: which provider(s) returned them and when | M | 1 |
| F-04 | Canonical resource model; multiple providers can reference one resource | M | 1 |
| F-05 | Deduplication by DOI, ISBN, canonical URL, then normalized title + authors | M | 1 |
| F-06 | Access classification (see taxonomy below) separated from price and permissions | M | 1 |
| F-07 | Filters: type, year, language, open access, free/paid, provider | M | 1 |
| F-08 | Ranking with explainable signals; "why recommended" shown | M | 1 |
| F-09 | Resource detail page with all known locations and access options | M | 1 |
| F-10 | Accounts, collections, bookmarks, notes | M | 2 |
| F-11 | PDF and EPUB reader for authorized files; reading progress | M | 2 |
| F-12 | Access state machine and download orchestration with human-in-the-loop | S | 3 |
| F-13 | Local-LLM source-grounded Q&A, never fabricating citations | S | 4 |
| F-14 | Learning paths, quizzes, progress | S | 5 |
| F-15 | Research completeness dashboard with documented scoring | S | 6 |
| F-16 | Video, podcast, course, news, GitHub discovery | C | 7 |
| F-17 | Saved searches, monitoring, notifications | C | 8 |
| F-18 | Shared workspaces | C | 9 |

## Access taxonomy (F-06)

Public domain · Open access · Free with verified permission · Free to read online · Free registration · CAPTCHA required · Waiting period · Authentication required · Institutional · Subscription · Purchase · Rental · Library borrowing · User-owned · Metadata only · Restricted · Unknown.

Tracked separately: availability, price, read permission, download permission, redistribution permission. Default is **Unknown**; a resource is only labelled free or open when a provider asserts it.

## Non-functional

| ID | Requirement |
|---|---|
| N-01 | Search returns first provider results progressively; per-provider timeout (default 8 s) |
| N-02 | WCAG 2.2 AA target; keyboard navigable; semantic HTML |
| N-03 | Mobile-first, usable on low bandwidth; no client-side GPU or model requirement |
| N-04 | No secrets in source; configuration via environment |
| N-05 | All external content treated as untrusted (sanitized, never executed) |
| N-06 | Provider calls: timeout, retry with exponential backoff, rate limit, circuit breaker, caching |
| N-07 | Windows-friendly setup with PowerShell commands |
| N-08 | Every implemented feature has automated tests; status doc reflects reality |

## Out of scope for the MVP

Paid APIs, Kubernetes, graph database, multi-language UI, collaboration, billing.
