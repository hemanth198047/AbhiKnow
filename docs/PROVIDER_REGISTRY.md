# Provider Registry

Every provider needs its terms checked before integration. **Verification column reflects reality: nothing is verified yet.** Facts in "Known constraints" are from general knowledge and must be confirmed against the linked documentation.

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
