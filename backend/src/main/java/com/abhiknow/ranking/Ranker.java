package com.abhiknow.ranking;

import com.abhiknow.domain.AccessType;
import com.abhiknow.domain.Resource;
import com.abhiknow.domain.ResourceType;
import com.abhiknow.domain.SourceRef;
import com.abhiknow.resource.Text;
import com.abhiknow.search.ParsedQuery;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Year;
import java.util.*;

/**
 * Explainable ranking. score = 0.45*relevance + 0.25*access + 0.15*corroboration + 0.10*recency + 0.05*completeness.
 * relevance = 0.55*titleTermCoverage + 0.45*providerPosition. Citation counts are displayed but never used:
 * citations do not establish correctness. Every score comes with human-readable reasons.
 */
@Component
public class Ranker {
    private final Clock clock;

    public Ranker() { this(Clock.systemUTC()); }

    Ranker(Clock clock) { this.clock = clock; }

    public List<Resource> rank(List<Resource> in, ParsedQuery q, int pageSize) {
        List<String> terms = Text.terms(q.mode() == ParsedQuery.Mode.KEYWORD || q.mode() == ParsedQuery.Mode.AUTHOR ? q.text() : "");
        List<Resource> out = new ArrayList<>();
        for (Resource r : in) out.add(score(r, terms, pageSize));
        out.sort(Comparator.comparingDouble(Resource::score).reversed().thenComparing(Resource::title, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    Resource score(Resource r, List<String> terms, int pageSize) {
        List<String> reasons = new ArrayList<>();

        double coverage = 0;
        if (!terms.isEmpty()) {
            Set<String> titleTerms = new HashSet<>(Text.terms(r.title() + " " + (r.subtitle() == null ? "" : r.subtitle())));
            long hit = terms.stream().filter(titleTerms::contains).count();
            coverage = (double) hit / terms.size();
            if (hit > 0) reasons.add("Title matches " + hit + " of " + terms.size() + " search terms");
        }
        int bestRank = r.sources().stream().mapToInt(SourceRef::rank).min().orElse(Integer.MAX_VALUE);
        double position = bestRank == Integer.MAX_VALUE ? 0 : Math.max(0, 1.0 - (double) (bestRank - 1) / Math.max(1, pageSize));
        if (bestRank <= 3) {
            SourceRef top = r.sources().stream().min(Comparator.comparingInt(SourceRef::rank)).orElseThrow();
            reasons.add("Ranked #" + bestRank + " by " + top.providerId());
        }
        double relevance = 0.55 * coverage + 0.45 * position;

        double access = accessWeight(r.accessType());
        if (access >= 0.9) reasons.add(r.accessType().label() + ": can be read for free");
        else if (r.accessType() == AccessType.LIBRARY_BORROWING) reasons.add("Can be borrowed from a library");

        long providers = r.sources().stream().map(SourceRef::providerId).distinct().count();
        double corroboration = Math.min(1.0, (providers - 1) / 2.0);
        if (providers > 1) reasons.add("Found by " + providers + " independent sources");

        double recency = recencyWeight(r);
        if (r.year() != null && recency >= 0.9) reasons.add("Recent (" + r.year() + ")");

        double completeness = (r.authors().isEmpty() ? 0 : 0.4) + (r.year() == null ? 0 : 0.3) + (r.description() == null ? 0 : 0.3);

        double score = 0.45 * relevance + 0.25 * access + 0.15 * corroboration + 0.10 * recency + 0.05 * completeness;
        score = Math.round(score * 1000.0) / 1000.0;
        if (r.citedBy() != null && r.citedBy() > 0) reasons.add("Cited " + r.citedBy() + " times (shown for context; not used for ranking)");
        return r.withRanking(score, List.copyOf(reasons));
    }

    static double accessWeight(AccessType t) {
        return switch (t) {
            case PUBLIC_DOMAIN, OPEN_ACCESS, FREE_VERIFIED_PERMISSION -> 1.0;
            case FREE_TO_READ_ONLINE -> 0.9;
            case FREE_REGISTRATION -> 0.7;
            case LIBRARY_BORROWING, USER_OWNED -> 0.5;
            case METADATA_ONLY, RESTRICTED -> 0.1;
            default -> 0.2;
        };
    }

    private double recencyWeight(Resource r) {
        boolean timeSensitive = switch (r.type()) {
            case PAPER, PREPRINT, NEWS, DATASET, THESIS, CODE, DOCUMENTATION -> true;
            default -> false;
        };
        if (!timeSensitive) return 0.5;           // books and courses: age is not a quality signal
        if (r.year() == null) return 0.3;
        int age = Math.max(0, Year.now(clock).getValue() - r.year());
        if (age <= 2) return 1.0;
        if (age >= 25) return 0.2;
        return 1.0 - 0.8 * (age - 2) / 23.0;
    }
}
