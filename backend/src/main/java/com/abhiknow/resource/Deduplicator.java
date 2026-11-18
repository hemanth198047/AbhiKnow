package com.abhiknow.resource;

import com.abhiknow.access.AccessClassifier;
import com.abhiknow.domain.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Entity resolution. Strong keys (DOI, ISBN, arXiv id, provider ref, URL identifiers) merge records into one
 * resource. Fuzzy title+first-author matching merges only within the same type and a compatible year.
 * Preprint vs published paper, and different editions of a book, are kept separate and linked by Relation.
 */
@Component
public class Deduplicator {
    static final double TITLE_THRESHOLD = 0.92;

    public List<Resource> merge(List<ProviderRecord> records) {
        int n = records.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        // Phase 1: strong identifiers
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < n; i++) {
            ProviderRecord r = records.get(i);
            List<String> keys = new ArrayList<>();
            keys.add("REF:" + r.providerId() + "|" + r.providerRef());
            for (Identifier id : r.identifiers()) keys.add(id.scheme() + ":" + id.value());
            for (String k : keys) {
                Integer prev = seen.putIfAbsent(k, i);
                if (prev != null) union(parent, prev, i);
            }
        }

        // Phase 2: fuzzy title + first author, same type, compatible year
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (find(parent, i) == find(parent, j)) continue;
                ProviderRecord a = records.get(i), b = records.get(j);
                // Different types (e.g. preprint vs published paper) are never fuzzy-merged; they get a Relation instead.
                if (a.type() == b.type() && sameWork(a, b) && yearsCompatible(a, b, true)) {
                    union(parent, i, j);
                }
            }
        }

        Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);

        List<Group> merged = new ArrayList<>();
        for (List<Integer> idx : groups.values()) {
            merged.add(new Group(idx.stream().map(records::get).toList(), "r" + idx.get(0)));
        }

        // Phase 3: relations between groups that were not merged
        Map<String, List<Relation>> relations = new HashMap<>();
        for (int a = 0; a < merged.size(); a++) {
            for (int b = a + 1; b < merged.size(); b++) {
                Group ga = merged.get(a), gb = merged.get(b);
                relate(ga, gb, relations);
            }
        }

        List<Resource> out = new ArrayList<>();
        for (Group g : merged) out.add(g.toResource(relations.getOrDefault(g.key, List.of())));
        return out;
    }

    private void relate(Group a, Group b, Map<String, List<Relation>> rel) {
        Group pre = a.primary.type() == ResourceType.PREPRINT ? a : b.primary.type() == ResourceType.PREPRINT ? b : null;
        Group pub = pre == a ? b : a;
        if (pre != null && pub.primary.type() == ResourceType.PAPER) {
            boolean doiLink = pre.records.stream().flatMap(r -> r.relatedDois().stream())
                    .anyMatch(d -> pub.doiSet().contains(d));
            if (doiLink || (sameWork(pre.primary, pub.primary) && yearsCompatible(pre.primary, pub.primary, false))) {
                rel.computeIfAbsent(pre.key, k -> new ArrayList<>())
                        .add(new Relation(Relation.PREPRINT_OF, pub.key, pub.primary.title()));
                return;
            }
        }
        if (a.primary.type() == ResourceType.BOOK && b.primary.type() == ResourceType.BOOK
                && sameWork(a.primary, b.primary)) {
            rel.computeIfAbsent(a.key, k -> new ArrayList<>()).add(new Relation(Relation.EDITION, b.key, b.primary.title()));
            rel.computeIfAbsent(b.key, k -> new ArrayList<>()).add(new Relation(Relation.EDITION, a.key, a.primary.title()));
        }
    }

    /** Same title (>= 0.92 similar; short titles must be equal) and same first-author surname. */
    static boolean sameWork(ProviderRecord a, ProviderRecord b) {
        String ta = Text.normalize(a.title()), tb = Text.normalize(b.title());
        if (ta.isEmpty() || tb.isEmpty()) return false;
        boolean titleOk = Math.min(ta.length(), tb.length()) < 12 ? ta.equals(tb) : Text.similarity(ta, tb) >= TITLE_THRESHOLD;
        if (!titleOk) return false;
        if (a.authors().isEmpty() || b.authors().isEmpty()) return false;
        String la = Text.lastName(a.authors().get(0)), lb = Text.lastName(b.authors().get(0));
        return !la.isEmpty() && la.equals(lb);
    }

    /** Books: unknown or equal years merge, differing years are editions. Others: within one year. */
    static boolean yearsCompatible(ProviderRecord a, ProviderRecord b, boolean strictForBooks) {
        if (a.year() == null || b.year() == null) return true;
        if (a.type() == ResourceType.BOOK && strictForBooks) return a.year().equals(b.year());
        return Math.abs(a.year() - b.year()) <= (strictForBooks ? 1 : 2);
    }

    private static int find(int[] p, int x) {
        while (p[x] != x) { p[x] = p[p[x]]; x = p[x]; }
        return x;
    }

    private static void union(int[] p, int a, int b) {
        int ra = find(p, a), rb = find(p, b);
        if (ra != rb) p[Math.max(ra, rb)] = Math.min(ra, rb);
    }

    /** Records that resolved to one resource. */
    private static final class Group {
        final List<ProviderRecord> records;
        final String key;
        final ProviderRecord primary;

        Group(List<ProviderRecord> records, String key) {
            this.records = records;
            this.key = key;
            this.primary = records.stream().max(Comparator.comparingInt(Group::richness)).orElseThrow();
        }

        static int richness(ProviderRecord r) {
            return (r.authors().isEmpty() ? 0 : 3) + (r.description() != null ? 3 : 0) + (r.year() != null ? 2 : 0)
                    + (r.type() != ResourceType.OTHER ? 2 : 0) + Math.min(r.locations().size(), 3);
        }

        Set<String> doiSet() {
            Set<String> s = new HashSet<>();
            for (ProviderRecord r : records) for (Identifier i : r.identifiers()) if (i.scheme() == IdScheme.DOI) s.add(i.value());
            return s;
        }

        Resource toResource(List<Relation> relations) {
            LinkedHashSet<Identifier> ids = new LinkedHashSet<>();
            LinkedHashMap<String, Location> locs = new LinkedHashMap<>();
            List<SourceRef> sources = new ArrayList<>();
            LinkedHashSet<String> subjects = new LinkedHashSet<>();
            String description = null, language = null, publisher = null, subtitle = null;
            Integer year = null, cited = null;
            for (ProviderRecord r : records) {
                ids.addAll(r.identifiers());
                for (Location l : r.locations()) locs.putIfAbsent(l.url(), l);
                sources.add(new SourceRef(r.providerId(), r.providerRef(), r.rank()));
                subjects.addAll(r.subjects());
                if (r.description() != null && (description == null || r.description().length() > description.length())) description = r.description();
                if (language == null) language = r.language();
                if (publisher == null) publisher = r.publisher();
                if (subtitle == null) subtitle = r.subtitle();
                if (cited == null || (r.citedBy() != null && r.citedBy() > cited)) cited = r.citedBy();
            }
            year = primary.year();
            if (year == null) for (ProviderRecord r : records) if (r.year() != null) { year = r.year(); break; }
            List<String> authors = primary.authors();
            if (authors.isEmpty()) for (ProviderRecord r : records) if (!r.authors().isEmpty()) { authors = r.authors(); break; }
            if (primary.language() != null) language = primary.language();
            if (primary.publisher() != null) publisher = primary.publisher();
            List<Location> locations = List.copyOf(locs.values());
            return new Resource(null, key, primary.title(), subtitle != null ? subtitle : primary.subtitle(), description,
                    primary.type(), language, publisher, year, authors, List.copyOf(ids), locations, sources,
                    subjects.stream().limit(12).toList(), cited, AccessClassifier.best(locations), relations, 0, List.of());
        }
    }
}
