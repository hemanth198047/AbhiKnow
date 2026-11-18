# Licensing and Dependency Register

Project license: **MIT**. Only dependencies with permissive or compatible licenses are accepted without review. "Verified" means the license was checked against the project's own repository or package metadata. Nothing below is verified yet; this is the intended list.

| Dependency | Purpose | Expected license | Verified | Concerns |
|---|---|---|---|---|
| Spring Boot | Backend framework | Apache-2.0 | No | None expected |
| PostgreSQL | Database | PostgreSQL License | No | None |
| Flyway (community) | Migrations | Apache-2.0 | No | Some features are paid; use community only |
| React, Vite, TanStack Query, React Router | Frontend | MIT | No | None expected |
| Tailwind CSS | Styling | MIT | No | None expected |
| PDF.js | PDF reader | Apache-2.0 | No | Phase 2 |
| EPUB reader library | EPUB reader | TBD | No | Choose a maintained option in Phase 2 |
| Apache Tika | Document parsing | Apache-2.0 | No | Phase 3 |
| pypdf | PDF parsing | BSD-3 | No | Phase 4 |
| PyMuPDF | PDF parsing | AGPL-3.0 / commercial | No | **AGPL: needs explicit approval before use** |
| Ollama / llama.cpp | Local LLM | MIT | No | Model licenses vary and are separate |
| Playwright | Testing | Apache-2.0 | No | None expected |

## Content licensing

Data returned by providers keeps the provider's license and attribution requirements. Free availability does not grant redistribution rights. Each resource location stores license text and read/download/redistribute permissions separately, defaulting to `UNKNOWN`.

## Rule

No paid dependency may be added without explicit approval from the project owner.
