# AbhiKnow

**The World's Knowledge. One Intelligent Companion.**

AbhiKnow is a research and learning platform. You enter a topic, and it finds books, papers, courses and other resources across open providers. It tells you plainly what you can read for free, what costs money, and what is restricted.

> **Status: Phase 0 (foundation).** No application code exists yet. See [IMPLEMENTATION_STATUS](docs/IMPLEMENTATION_STATUS.md) for what is built and verified. Nothing is claimed as working until it has been tested.

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

## Local development (Windows PowerShell)

Prerequisites: JDK 21, Maven 3.9+, Node 20+, Docker Desktop.

```powershell
Copy-Item .env.example .env
docker compose -f infrastructure/docker-compose.yml up -d postgres
# backend and frontend commands are added in Phase 1
```

## License

MIT, see [LICENSE](LICENSE).
