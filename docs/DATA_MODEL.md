# Data Model

Migrations live in `database/migrations` (Flyway). Large binaries are never stored in PostgreSQL.

## Implemented in V1 (Phase 0 schema, used from Phase 1)

| Table | Purpose |
|---|---|
| `provider` | Provider registry and trust status |
| `resource` | Canonical resource (title, type, language, year, description, access summary) |
| `resource_identifier` | DOI, ISBN, arXiv ID, OpenAlex ID, etc. with uniqueness per scheme+value |
| `resource_author` | Ordered authors per resource |
| `resource_location` | Where a resource can be accessed: URL, provider, format, access type, license, verified-at |
| `resource_source` | Provenance: which provider returned the resource, when, raw provider ID |
| `search_session` | A query, filters, and per-provider outcomes |
| `provider_usage` | Request counts, failures, latency per provider per day |
| `audit_event` | Security-relevant and state-change events |

## Planned (later phases)

User, UserPreferences, Workspace, ResearchProject, Collection, Bookmark, Annotation, ReadingProgress (Phase 2); AccessSession, AccessAttempt, HumanActionRequest, DownloadJob, DownloadArtifact, ProviderTrustRecord (Phase 3); Citation, embeddings (Phase 4); StudyPlan, StudyModule, LearningObjective, Quiz, QuizAttempt (Phase 5); Topic, TopicRelationship, KnowledgeGap, ResearchCoverage (Phase 6); MonitoringSubscription, Notification, BackgroundJob (Phase 8).

## Conventions

UUID primary keys; `created_at`/`updated_at` timestamptz; foreign keys enforced; enums stored as text with CHECK constraints so migrations stay simple; provenance kept per source.
