# API

Base path `/api`. JSON only. Errors use RFC 7807 problem details (`detail` is safe to show).

## GET /api/search
| Param | Notes |
|---|---|
| `q` | Required, max 300 chars. Keywords, DOI, ISBN, arXiv id, `author:"Name"`, or quoted phrases |
| `page` | 1 to 20, default 1 |
| `types` | Repeatable: BOOK, PAPER, PREPRINT, THESIS, DATASET, OTHER… |
| `yearFrom`, `yearTo`, `language` | Applied after providers answer |
| `access` | `ANY` (default), `FREE` (provider-stated free to read), `OPEN` (open license or public domain) |
| `providers` | Optional repeatable provider ids |

Response: `query` (raw, searchedAs, mode), `results` (ranked, each with access, locations, reasons), `recommendedIds`, `countsByType`, `totalBeforeFilters`, `providers` (status per provider: OK, TIMEOUT, ERROR, RATE_LIMITED, SKIPPED, CIRCUIT_OPEN, DISABLED), `durationMs`, `coverage`, `warnings`.

## GET /api/resources/{id}
Full resource: identifiers, every location with `canRead`/`canDownload`/`canRedistribute` and a note, related versions, provenance. 404 if unknown.

## GET /api/providers
Trust registry rows plus whether the circuit breaker has paused the provider.

## GET /actuator/health
Liveness and readiness.
