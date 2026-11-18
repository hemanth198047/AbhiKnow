# Security

## Principles

All external content (provider responses, documents, web pages) is untrusted. Downloaded files are never executed. Retrieved text never overrides application rules (prompt-injection defence for later AI phases).

## Controls by phase

| Control | Phase | Status |
|---|---|---|
| Secrets via environment only; `.env` git-ignored | 0 | Implemented |
| Input validation on all API parameters; bounded query length and page size | 1 | Not started |
| Outbound HTTP only to allowlisted provider hosts; no user-supplied URLs fetched | 1 | Not started |
| Output encoding; no raw HTML from providers rendered | 1 | Not started |
| Rate limiting on search API | 1 | Not started |
| Authentication, hashed passwords, workspace isolation | 2 | Not started |
| SSRF prevention, redirect validation, size limits, MIME and signature checks, malware scanning, quotas | 3 | Not started |
| Audit logging of access and download events | 3 | Not started |

## Reporting

Open a private security advisory on the GitHub repository.
