# Provider Registry

Every provider needs its terms checked before integration. **Trust status stays `PENDING_REVIEW` for every provider: approval is a human decision.** The table below records what was actually checked on 2026-11-19 and what was not.

| Provider | Content | Docs | Auth | Known constraints (to verify) | Trust status | Terms verified |
|---|---|---|---|---|---|---|
| OpenAlex | Works, authors, OA status | https://docs.openalex.org | API key or email in polite pool | Generous free limits; check current daily caps | Pending review | No |
| Crossref | DOI metadata | https://api.crossref.org | None; include contact email | Polite pool for identified clients; respect rate headers | Pending review | No |
| arXiv | Preprints | https://info.arxiv.org/help/api | None | At most one request per 3 seconds; Atom XML | Pending review | No |
| Open Library | Books, editions | https://openlibrary.org/developers/api | None | Send descriptive User-Agent; avoid bulk crawling | Pending review | No |
| Project Gutenberg (via Gutendex) | Public-domain books | https://gutendex.com | None | Third-party API; Gutenberg asks that robots use its feeds responsibly | Pending review | No |
| PubMed / Europe PMC / DOAJ / Semantic Scholar / CORE | Academic | Various | Various | Planned, not started | Not started | No |
| Google Books, YouTube, GitHub | Books metadata, video, code | Various | API key | Planned for Phase 7; key and quota terms apply | Not started | No |

## Blocked providers

Sites associated with unauthorized distribution of copyrighted works (for example OceanofPDF) are `BLOCKED` and are never queried or downloaded from.

## Adapter requirements

Query translation, pagination, timeouts, retry with exponential backoff, rate limiting, caching, normalization, error isolation, health, usage tracking, provenance.


## Review notes, 2026-11-19

What was read from the provider's own pages, and what was observed by calling the API.

| Provider | Read from docs | Observed |
|---|---|---|
| OpenAlex | The help site (`help.openalex.org`) confirms keys exist and agents can request one; it did not state limits or license in the page read | Keyless call returned HTTP 429: shared per-IP daily budget used up. Adapter sends `api_key` only when `OPENALEX_API_KEY` is set. License and attribution terms not yet confirmed |
| Crossref | No sign-up needed; `mailto` selects the polite pool; "almost none of the metadata is subject to copyright", but some abstracts may be | Search and single-DOI routes work. The single-DOI route rejects `select` |
| arXiv | Required statement: "Thank you to arXiv for use of its open access interoperability." Do not use arXiv names or logos to suggest endorsement. Commercial projects should read the terms and consider affiliation | Atom API works over HTTPS (HTTP redirects). The one-request-per-3-seconds figure was not on the page read; it is applied conservatively and should be checked against the API Terms of Use |
| Open Library | Identify the app with a User-Agent and contact email: 3 requests/second when identified, 1 when not. Do not use the API for bulk download or as the backend of a high-traffic commercial service | Search works with the `fields` parameter |
| Project Gutenberg via Gutendex | Not read | Host unreachable from the build sandbox |

Consequences in code: contact email in every User-Agent, request spacing per provider, no bulk crawling (first page per search, cached for 10 minutes), the arXiv attribution in the site footer.

Open question for the owner: Open Library asks that its API not back a high-traffic commercial service. Fine for the first hundred users; revisit before commercial or public launch.
