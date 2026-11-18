# AbhiKnow

**The World's Knowledge. One Intelligent Companion.**

AbhiKnow is a research and learning platform. You enter a topic, and it finds books, papers, courses and other resources across open providers. It tells you plainly what you can read for free, what costs money, and what is restricted.

> **Status: Phase 1 (discovery MVP) implemented and tested.** Search across Crossref, arXiv and Open Library works against the live services; OpenAlex needs an API key and Gutenberg has not been run live. There are no accounts, library or reader yet (Phase 2). See [IMPLEMENTATION_STATUS](docs/IMPLEMENTATION_STATUS.md) and [KNOWN_LIMITATIONS](docs/KNOWN_LIMITATIONS.md). Nothing is claimed as working until it has been tested.

## Principles

Free and open-source first. Discovery before AI. The product must stay useful without an LLM. No bypassing paywalls, DRM or CAPTCHAs. No fabricated results. Details in [PRODUCT_VISION](docs/PRODUCT_VISION.md).

## Repository layout

| Path | Purpose |
|---|---|
| `backend/` | Java 21 + Spring Boot modular monolith (REST API, provider adapters) |
| `frontend/` | React + TypeScript + Vite + Tailwind |
| `workers/` | Python workers for parsing and AI (later phases) |
| `database/migrations/` | Flyway SQL migrations (PostgreSQL) |
| `infrastructure/` | Docker Compose and deployment config |
| `docs/` | Vision, requirements, architecture, registers |

## Documentation

- [Product vision](docs/PRODUCT_VISION.md) · [Requirements](docs/REQUIREMENTS.md) · [Architecture](docs/ARCHITECTURE.md)
- [Data model](docs/DATA_MODEL.md) · [Roadmap](docs/ROADMAP.md) · [Implementation status](docs/IMPLEMENTATION_STATUS.md)
- [Provider registry](docs/PROVIDER_REGISTRY.md) · [Licensing register](docs/LICENSING.md) · [Security](docs/SECURITY.md)
- [Competitive analysis](docs/COMPETITIVE_ANALYSIS.md) · [Contributing](docs/CONTRIBUTING.md) · [Known limitations](docs/KNOWN_LIMITATIONS.md)
- [API](docs/API_DOCUMENTATION.md) · [Test report](docs/TEST_REPORT.md)

## Local development (Windows PowerShell)

Prerequisites: JDK 21, Maven 3.9+, Node 20+, and PostgreSQL 15+ (either Docker Desktop or a local install).

```powershell
# 1. Configuration
Copy-Item .env.example .env        # edit POSTGRES_PASSWORD and ABHIKNOW_CONTACT_EMAIL

# 2. Database (Docker)
docker compose --env-file .env -f infrastructure/docker-compose.yml up -d postgres

# 3. Backend (http://localhost:8080). Flyway applies the migrations on start.
cd backend
$env:POSTGRES_PASSWORD = "<same as .env>"
$env:ABHIKNOW_CONTACT_EMAIL = "you@example.com"
mvn spring-boot:run

# 4. Frontend (http://localhost:5173), in a second terminal
cd frontend
npm install
npm run dev
```

Optional: `$env:OPENALEX_API_KEY = "<your free key>"` before starting the backend to include OpenAlex.

Tests:

```powershell
cd backend;  mvn test            # database tests run only if ABHIKNOW_TEST_DB_URL is set
cd frontend; npm test; npm run typecheck
```

To run the database tests, create an empty database and set `ABHIKNOW_TEST_DB_URL` (for example `jdbc:postgresql://localhost:5432/abhiknow_test`), `ABHIKNOW_TEST_DB_USER` and `ABHIKNOW_TEST_DB_PASSWORD`. The test **wipes that database's schema**, so never point it at data you care about.

## License

MIT, see [LICENSE](LICENSE).
