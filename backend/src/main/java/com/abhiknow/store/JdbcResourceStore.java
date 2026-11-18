package com.abhiknow.store;

import com.abhiknow.access.AccessClassifier;
import com.abhiknow.domain.*;
import com.abhiknow.provider.ProviderOutcome;
import com.abhiknow.resource.Text;
import com.abhiknow.search.ParsedQuery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.*;

@Repository
public class JdbcResourceStore implements ResourceStore {
    private final JdbcClient jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public JdbcResourceStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public List<Resource> saveAll(List<Resource> resources) {
        Map<String, UUID> idByKey = new HashMap<>();
        List<UUID> ids = new ArrayList<>();
        for (Resource r : resources) {
            UUID id = upsert(r);
            idByKey.put(r.key(), id);
            ids.add(id);
        }
        List<Resource> out = new ArrayList<>();
        for (int i = 0; i < resources.size(); i++) {
            Resource r = resources.get(i);
            UUID id = ids.get(i);
            List<Relation> resolved = new ArrayList<>();
            for (Relation rel : r.relations()) {
                UUID target = idByKey.get(rel.targetKey());
                if (target == null || target.equals(id)) continue;
                jdbc.sql("INSERT INTO resource_relation (resource_id, related_id, kind) VALUES (?,?,?) ON CONFLICT DO NOTHING")
                        .params(id, target, rel.kind()).update();
                resolved.add(new Relation(rel.kind(), target.toString(), rel.targetTitle()));
            }
            AccessType best = storedAccess(id);
            out.add(withAccess(r.withId(id).withRelations(resolved), best));
        }
        return out;
    }

    private UUID upsert(Resource r) {
        UUID id = findExisting(r);
        String normTitle = Text.normalize(r.title());
        if (id == null) {
            id = jdbc.sql("""
                    INSERT INTO resource (title, subtitle, description, resource_type, language, publisher, published_year,
                                          access_type, normalized_title, cited_by_count)
                    VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id""")
                    .params(r.title(), r.subtitle(), r.description(), r.type().name(), r.language(), r.publisher(), r.year(),
                            r.accessType().name(), normTitle, r.citedBy())
                    .query(UUID.class).single();
        } else {
            jdbc.sql("""
                    UPDATE resource SET title=?, subtitle=coalesce(?,subtitle), description=coalesce(?,description),
                        resource_type=?, language=coalesce(?,language), publisher=coalesce(?,publisher),
                        published_year=coalesce(?,published_year), normalized_title=?,
                        cited_by_count=coalesce(?,cited_by_count), updated_at=now()
                    WHERE id=?""")
                    .params(r.title(), r.subtitle(), r.description(), r.type().name(), r.language(), r.publisher(), r.year(),
                            normTitle, r.citedBy(), id).update();
        }
        for (Identifier i : r.identifiers()) {
            jdbc.sql("INSERT INTO resource_identifier (resource_id, scheme, value) VALUES (?,?,?) ON CONFLICT (scheme, value) DO NOTHING")
                    .params(id, i.scheme().name(), i.value()).update();
        }
        if (!r.authors().isEmpty()) {
            jdbc.sql("DELETE FROM resource_author WHERE resource_id=?").param(id).update();
            int pos = 0;
            for (String a : r.authors()) {
                jdbc.sql("INSERT INTO resource_author (resource_id, position, name) VALUES (?,?,?)").params(id, pos++, a).update();
            }
        }
        if (!r.subjects().isEmpty()) {
            jdbc.sql("DELETE FROM resource_subject WHERE resource_id=?").param(id).update();
            int pos = 0;
            for (String s : r.subjects()) {
                jdbc.sql("INSERT INTO resource_subject (resource_id, position, subject) VALUES (?,?,?)").params(id, pos++, s).update();
            }
        }
        for (SourceRef s : r.sources()) {
            jdbc.sql("""
                    INSERT INTO resource_source (resource_id, provider_id, provider_ref) VALUES (?,?,?)
                    ON CONFLICT (provider_id, provider_ref) DO UPDATE SET fetched_at = now()""")
                    .params(id, s.providerId(), s.providerRef()).update();
        }
        for (Location l : r.locations()) {
            jdbc.sql("""
                    INSERT INTO resource_location (resource_id, provider_id, url, kind, format, access_type, license,
                        can_read, can_download, can_redistribute, note, verified_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?, now())
                    ON CONFLICT (resource_id, url) DO UPDATE SET kind=excluded.kind, format=excluded.format,
                        access_type=excluded.access_type, license=excluded.license, can_read=excluded.can_read,
                        can_download=excluded.can_download, can_redistribute=excluded.can_redistribute,
                        note=excluded.note, verified_at=now()""")
                    .params(id, l.providerId(), l.url(), l.kind(), l.format(), l.accessType().name(), l.license(),
                            l.canRead().name(), l.canDownload().name(), l.canRedistribute().name(), l.note()).update();
        }
        AccessType best = storedAccess(id);
        jdbc.sql("UPDATE resource SET access_type=? WHERE id=?").params(best.name(), id).update();
        return id;
    }

    /** An existing row matches if any provider ref or any identifier (other than URL) is already stored. */
    private UUID findExisting(Resource r) {
        for (SourceRef s : r.sources()) {
            Optional<UUID> id = jdbc.sql("SELECT resource_id FROM resource_source WHERE provider_id=? AND provider_ref=?")
                    .params(s.providerId(), s.providerRef()).query(UUID.class).optional();
            if (id.isPresent()) return id.get();
        }
        for (Identifier i : r.identifiers()) {
            Optional<UUID> id = jdbc.sql("SELECT resource_id FROM resource_identifier WHERE scheme=? AND value=?")
                    .params(i.scheme().name(), i.value()).query(UUID.class).optional();
            if (id.isPresent()) return id.get();
        }
        return null;
    }

    private AccessType storedAccess(UUID id) {
        AccessType best = AccessType.UNKNOWN;
        for (String t : jdbc.sql("SELECT access_type FROM resource_location WHERE resource_id=?").param(id).query(String.class).list()) {
            best = AccessType.best(best, AccessType.valueOf(t));
        }
        return best;
    }

    private static Resource withAccess(Resource r, AccessType t) {
        return new Resource(r.id(), r.key(), r.title(), r.subtitle(), r.description(), r.type(), r.language(), r.publisher(),
                r.year(), r.authors(), r.identifiers(), r.locations(), r.sources(), r.subjects(), r.citedBy(), t,
                r.relations(), r.score(), r.reasons());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Resource> find(UUID id) {
        return jdbc.sql("SELECT * FROM resource WHERE id=?").param(id).query((rs, n) -> {
            List<String> authors = jdbc.sql("SELECT name FROM resource_author WHERE resource_id=? ORDER BY position").param(id).query(String.class).list();
            List<String> subjects = jdbc.sql("SELECT subject FROM resource_subject WHERE resource_id=? ORDER BY position").param(id).query(String.class).list();
            List<Identifier> ids = jdbc.sql("SELECT scheme, value FROM resource_identifier WHERE resource_id=? ORDER BY scheme, value").param(id)
                    .query((r2, i) -> new Identifier(IdScheme.valueOf(r2.getString("scheme")), r2.getString("value"))).list();
            List<Location> locs = jdbc.sql("SELECT * FROM resource_location WHERE resource_id=? ORDER BY access_type, kind, url").param(id)
                    .query((r2, i) -> new Location(r2.getString("provider_id"), r2.getString("url"), r2.getString("kind"),
                            r2.getString("format"), AccessType.valueOf(r2.getString("access_type")), r2.getString("license"),
                            Permission.valueOf(r2.getString("can_read")), Permission.valueOf(r2.getString("can_download")),
                            Permission.valueOf(r2.getString("can_redistribute")), r2.getString("note"))).list();
            locs = locs.stream().sorted(Comparator.comparingInt(l -> l.accessType().ordinal())).toList();
            List<SourceRef> sources = jdbc.sql("SELECT provider_id, provider_ref FROM resource_source WHERE resource_id=? ORDER BY provider_id").param(id)
                    .query((r2, i) -> new SourceRef(r2.getString("provider_id"), r2.getString("provider_ref"), 0)).list();
            List<Relation> relations = new ArrayList<>(jdbc.sql(
                    "SELECT rr.kind, rr.related_id, r.title FROM resource_relation rr JOIN resource r ON r.id=rr.related_id WHERE rr.resource_id=?")
                    .param(id).query((r2, i) -> new Relation(r2.getString("kind"), r2.getString("related_id"), r2.getString("title"))).list());
            relations.addAll(jdbc.sql(
                    "SELECT rr.kind, rr.resource_id, r.title FROM resource_relation rr JOIN resource r ON r.id=rr.resource_id WHERE rr.related_id=?")
                    .param(id).query((r2, i) -> {
                        String k = r2.getString("kind");
                        return new Relation(k.equals(Relation.PREPRINT_OF) ? Relation.HAS_PREPRINT : k,
                                r2.getString("resource_id"), r2.getString("title"));
                    }).list());
            int y = rs.getInt("published_year");
            Integer year = rs.wasNull() ? null : y;
            int c = rs.getInt("cited_by_count");
            Integer cited = rs.wasNull() ? null : c;
            return new Resource(id, id.toString(), rs.getString("title"), rs.getString("subtitle"), rs.getString("description"),
                    ResourceType.valueOf(rs.getString("resource_type")), rs.getString("language"), rs.getString("publisher"), year,
                    authors, ids, locs, sources, subjects, cited, AccessClassifier.best(locs), relations, 0, List.of());
        }).optional();
    }

    @Override
    public void recordSearch(ParsedQuery q, Map<String, Object> filters, List<ProviderOutcome> outcomes, int resultCount, long durationMs) {
        try {
            jdbc.sql("""
                    INSERT INTO search_session (query, filters, provider_outcomes, result_count, duration_ms)
                    VALUES (?, ?::jsonb, ?::jsonb, ?, ?)""")
                    .params(q.raw(), json.writeValueAsString(filters), json.writeValueAsString(outcomes), resultCount, (int) durationMs)
                    .update();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void recordUsage(ProviderOutcome o) {
        if (o.cached() || o.status() == ProviderOutcome.Status.SKIPPED || o.status() == ProviderOutcome.Status.DISABLED
                || o.status() == ProviderOutcome.Status.CIRCUIT_OPEN) return;
        boolean failed = o.status() != ProviderOutcome.Status.OK;
        jdbc.sql("""
                INSERT INTO provider_usage (provider_id, day, requests, failures, total_latency_ms)
                VALUES (?, CURRENT_DATE, 1, ?, ?)
                ON CONFLICT (provider_id, day) DO UPDATE SET requests = provider_usage.requests + 1,
                    failures = provider_usage.failures + excluded.failures,
                    total_latency_ms = provider_usage.total_latency_ms + excluded.total_latency_ms""")
                .params(o.providerId(), failed ? 1 : 0, o.latencyMs()).update();
    }

    @Override
    public List<ProviderInfo> providers() {
        return jdbc.sql("SELECT * FROM provider ORDER BY id").query((rs, n) -> {
            Date d = rs.getDate("last_reviewed");
            return new ProviderInfo(rs.getString("id"), rs.getString("display_name"), rs.getString("base_url"),
                    rs.getString("trust_status"), rs.getString("terms_url"), rs.getBoolean("automation_allowed"),
                    d == null ? null : d.toString(), rs.getString("notes"));
        }).list();
    }
}
