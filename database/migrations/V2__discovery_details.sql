-- AbhiKnow V2: fields and tables needed by the Phase 1 discovery MVP

ALTER TABLE resource ADD COLUMN cited_by_count integer;

ALTER TABLE resource_location
    ADD COLUMN kind text,
    ADD COLUMN note text;

CREATE TABLE resource_subject (
    resource_id uuid NOT NULL REFERENCES resource(id) ON DELETE CASCADE,
    position    integer NOT NULL,
    subject     text NOT NULL,
    PRIMARY KEY (resource_id, position)
);

-- Related-but-distinct resources (preprint vs published version, editions). Never merged.
CREATE TABLE resource_relation (
    resource_id uuid NOT NULL REFERENCES resource(id) ON DELETE CASCADE,
    related_id  uuid NOT NULL REFERENCES resource(id) ON DELETE CASCADE,
    kind        text NOT NULL CHECK (kind IN ('PREPRINT_OF','EDITION','VERSION_OF','TRANSLATION')),
    PRIMARY KEY (resource_id, related_id, kind),
    CHECK (resource_id <> related_id)
);
CREATE INDEX resource_relation_related_idx ON resource_relation (related_id);

-- Provider terms were partially reviewed on 2026-11-19; see docs/PROVIDER_REGISTRY.md.
-- Trust status stays PENDING_REVIEW: approval is a human legal decision, not made by code.
UPDATE provider SET last_reviewed = DATE '2026-11-19',
    notes = 'Needs a free API key in practice; shared no-key budget can be exhausted (observed HTTP 429).'
 WHERE id = 'openalex';
UPDATE provider SET last_reviewed = DATE '2026-11-19',
    notes = 'No sign-up. Send mailto for the polite pool. Some abstracts may be copyrighted.'
 WHERE id = 'crossref';
UPDATE provider SET last_reviewed = DATE '2026-11-19',
    notes = 'Attribution required: "Thank you to arXiv for use of its open access interoperability." One request per 3 seconds.'
 WHERE id = 'arxiv';
UPDATE provider SET last_reviewed = DATE '2026-11-19',
    notes = 'Identified User-Agent with contact email: 3 requests/second. Not for bulk download.'
 WHERE id = 'openlibrary';
UPDATE provider SET last_reviewed = DATE '2026-11-19',
    notes = 'Third-party Gutendex API; was unreachable from the build sandbox, so live behaviour is unverified.'
 WHERE id = 'gutenberg';
