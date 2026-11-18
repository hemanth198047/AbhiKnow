-- AbhiKnow V1: core discovery schema (PostgreSQL 15+)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE provider (
    id              text PRIMARY KEY,
    display_name    text NOT NULL,
    base_url        text NOT NULL,
    trust_status    text NOT NULL DEFAULT 'PENDING_REVIEW'
        CHECK (trust_status IN ('APPROVED','PENDING_REVIEW','BLOCKED')),
    terms_url       text,
    automation_allowed boolean NOT NULL DEFAULT false,
    last_reviewed   date,
    notes           text
);

CREATE TABLE resource (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title         text NOT NULL,
    subtitle      text,
    description   text,
    resource_type text NOT NULL DEFAULT 'OTHER'
        CHECK (resource_type IN ('BOOK','PAPER','PREPRINT','THESIS','COURSE','VIDEO','AUDIO','NEWS','CODE','DATASET','DOCUMENTATION','OTHER')),
    language      text,
    publisher     text,
    published_year integer,
    published_date date,
    access_type   text NOT NULL DEFAULT 'UNKNOWN',
    normalized_title text NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX resource_norm_title_idx ON resource (normalized_title);
CREATE INDEX resource_fts_idx ON resource
    USING gin (to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(description,'')));

CREATE TABLE resource_identifier (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_id uuid NOT NULL REFERENCES resource(id) ON DELETE CASCADE,
    scheme      text NOT NULL CHECK (scheme IN ('DOI','ISBN','ARXIV','OPENALEX','OPENLIBRARY','GUTENBERG','PMID','URL')),
    value       text NOT NULL,
    UNIQUE (scheme, value)
);
CREATE INDEX resource_identifier_resource_idx ON resource_identifier (resource_id);

CREATE TABLE resource_author (
    resource_id uuid NOT NULL REFERENCES resource(id) ON DELETE CASCADE,
    position    integer NOT NULL,
    name        text NOT NULL,
    PRIMARY KEY (resource_id, position)
);

CREATE TABLE resource_location (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_id uuid NOT NULL REFERENCES resource(id) ON DELETE CASCADE,
    provider_id text NOT NULL REFERENCES provider(id),
    url         text NOT NULL,
    format      text,
    access_type text NOT NULL DEFAULT 'UNKNOWN',
    license     text,
    can_read    text NOT NULL DEFAULT 'UNKNOWN' CHECK (can_read IN ('YES','NO','UNKNOWN')),
    can_download text NOT NULL DEFAULT 'UNKNOWN' CHECK (can_download IN ('YES','NO','UNKNOWN')),
    can_redistribute text NOT NULL DEFAULT 'UNKNOWN' CHECK (can_redistribute IN ('YES','NO','UNKNOWN')),
    price_amount numeric(10,2),
    price_currency text,
    verified_at timestamptz,
    UNIQUE (resource_id, url)
);

CREATE TABLE resource_source (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_id  uuid NOT NULL REFERENCES resource(id) ON DELETE CASCADE,
    provider_id  text NOT NULL REFERENCES provider(id),
    provider_ref text NOT NULL,
    fetched_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (provider_id, provider_ref)
);

CREATE TABLE search_session (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    query       text NOT NULL,
    filters     jsonb NOT NULL DEFAULT '{}'::jsonb,
    provider_outcomes jsonb NOT NULL DEFAULT '[]'::jsonb,
    result_count integer NOT NULL DEFAULT 0,
    duration_ms integer,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE provider_usage (
    provider_id text NOT NULL REFERENCES provider(id),
    day         date NOT NULL,
    requests    integer NOT NULL DEFAULT 0,
    failures    integer NOT NULL DEFAULT 0,
    total_latency_ms bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (provider_id, day)
);

CREATE TABLE audit_event (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type text NOT NULL,
    subject    text,
    details    jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO provider (id, display_name, base_url, trust_status, automation_allowed, notes) VALUES
 ('openalex','OpenAlex','https://api.openalex.org','PENDING_REVIEW',true,'Terms to verify before Phase 1 integration'),
 ('crossref','Crossref','https://api.crossref.org','PENDING_REVIEW',true,'Use polite pool with contact email'),
 ('arxiv','arXiv','https://export.arxiv.org/api','PENDING_REVIEW',true,'Max one request per 3 seconds'),
 ('openlibrary','Open Library','https://openlibrary.org','PENDING_REVIEW',true,'Identify app via User-Agent'),
 ('gutenberg','Project Gutenberg (via Gutendex)','https://gutendex.com','PENDING_REVIEW',true,'Gutendex is a third-party mirror API; verify availability');
