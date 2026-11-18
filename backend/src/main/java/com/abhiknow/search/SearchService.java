package com.abhiknow.search;

import com.abhiknow.config.AbhiKnowProperties;
import com.abhiknow.domain.AccessType;
import com.abhiknow.domain.Resource;
import com.abhiknow.domain.ProviderRecord;
import com.abhiknow.provider.ProviderOutcome;
import com.abhiknow.provider.ProviderOutcome.Status;
import com.abhiknow.provider.ProviderRuntime;
import com.abhiknow.provider.SearchProvider;
import com.abhiknow.ranking.Ranker;
import com.abhiknow.resource.Deduplicator;
import com.abhiknow.store.ResourceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Orchestrates one search: parse, fan out to providers in parallel (bounded), isolate failures,
 * normalize/dedupe/rank, filter, persist, and report exactly which providers answered.
 */
@Service
public class SearchService {
    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final List<SearchProvider> providers;
    private final ProviderRuntime runtime;
    private final QueryParser parser;
    private final Deduplicator dedup;
    private final Ranker ranker;
    private final ResourceStore store;
    private final AbhiKnowProperties props;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore permits;

    public SearchService(List<SearchProvider> providers, ProviderRuntime runtime, QueryParser parser, Deduplicator dedup,
                         Ranker ranker, ResourceStore store, AbhiKnowProperties props) {
        this.providers = providers;
        this.runtime = runtime;
        this.parser = parser;
        this.dedup = dedup;
        this.ranker = ranker;
        this.store = store;
        this.props = props;
        this.permits = new Semaphore(props.maxConcurrentProviderCalls());
    }

    public SearchResponse search(SearchRequest req) {
        long start = System.nanoTime();
        ParsedQuery pq = parser.parse(req.q());
        List<String> warnings = new ArrayList<>();

        Map<String, String> trust = new HashMap<>();
        try {
            store.providers().forEach(p -> trust.put(p.id(), p.trustStatus()));
        } catch (RuntimeException e) {
            log.warn("Provider registry unavailable: {}", e.toString());
            warnings.add("Provider registry unavailable; all registered providers were queried.");
            providers.forEach(p -> trust.put(p.id(), "PENDING_REVIEW"));
        }

        Map<String, Future<ProviderRuntime.Result>> futures = new LinkedHashMap<>();
        List<ProviderOutcome> outcomes = new ArrayList<>();
        for (SearchProvider p : providers) {
            String t = trust.get(p.id());
            if (req.providers() != null && !req.providers().isEmpty() && !req.providers().contains(p.id())) continue;
            if (t == null || t.equals("BLOCKED")) {
                outcomes.add(outcome(p, Status.DISABLED, t == null ? "Not in the provider trust registry" : "Blocked by provider trust registry"));
            } else if (!p.supports(pq)) {
                outcomes.add(outcome(p, Status.SKIPPED, "This provider cannot answer " + pq.mode().name().toLowerCase() + " queries"));
            } else {
                futures.put(p.id(), executor.submit(() -> runBounded(p, pq, req.page())));
            }
        }

        List<ProviderRecord> records = new ArrayList<>();
        long hardWaitMs = props.providerTimeoutMs() + 2000L;
        for (SearchProvider p : providers) {
            Future<ProviderRuntime.Result> f = futures.get(p.id());
            if (f == null) continue;
            try {
                ProviderRuntime.Result r = f.get(hardWaitMs, TimeUnit.MILLISECONDS);
                outcomes.add(r.outcome());
                records.addAll(r.records());
            } catch (TimeoutException e) {
                f.cancel(true);
                outcomes.add(outcome(p, Status.TIMEOUT, "Provider did not answer in time"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                outcomes.add(outcome(p, Status.ERROR, "Interrupted"));
            } catch (ExecutionException e) {
                log.warn("Provider {} failed unexpectedly", p.id(), e.getCause());
                outcomes.add(outcome(p, Status.ERROR, "Unexpected provider failure"));
            }
        }
        outcomes.sort(Comparator.comparing(ProviderOutcome::providerId));

        List<Resource> merged = dedup.merge(records);
        List<Resource> ranked = ranker.rank(merged, pq, props.pageSize());
        int totalBefore = ranked.size();
        List<Resource> filtered = ranked.stream().filter(r -> matches(r, req)).toList();

        List<Resource> persisted;
        try {
            persisted = store.saveAll(filtered);
        } catch (RuntimeException e) {
            log.error("Could not persist search results", e);
            warnings.add("Results could not be saved; detail pages are unavailable for this search.");
            persisted = filtered;
        }
        // saveAll keeps order but ranking fields are carried on the resources themselves.
        List<ResourceDto> dtos = persisted.stream().map(ResourceDto::from).toList();
        Map<String, Integer> counts = new TreeMap<>();
        for (Resource r : persisted) counts.merge(r.type().name(), 1, Integer::sum);

        long ms = (System.nanoTime() - start) / 1_000_000;
        try {
            outcomes.forEach(store::recordUsage);
            store.recordSearch(pq, filterMap(req), outcomes, dtos.size(), ms);
        } catch (RuntimeException e) {
            log.warn("Could not record search analytics: {}", e.toString());
        }

        long ok = outcomes.stream().filter(o -> o.status() == Status.OK).count();
        long asked = outcomes.stream().filter(o -> o.status() != Status.SKIPPED && o.status() != Status.DISABLED).count();
        String coverage = "Searched " + ok + " of " + asked + " providers that could answer this query. "
                + "AbhiKnow searches a limited set of open sources; results are not exhaustive.";
        return new SearchResponse(new SearchResponse.QueryInfo(pq.raw(), pq.text(), pq.mode().name()), req.page(), dtos, recommended(dtos), counts,
                totalBefore, outcomes, ms, coverage, warnings);
    }

    private ProviderRuntime.Result runBounded(SearchProvider p, ParsedQuery pq, int page) throws InterruptedException {
        permits.acquire();
        try {
            return runtime.execute(p, pq, page, props.pageSize());
        } finally {
            permits.release();
        }
    }

    /** Starting points: the two best-ranked results of each type, at most six, so one format cannot crowd out the rest. */
    static List<String> recommended(List<ResourceDto> ranked) {
        Map<String, Integer> perType = new HashMap<>();
        List<ResourceDto> picks = new ArrayList<>();
        for (ResourceDto d : ranked) {
            if (d.id() == null || d.type().equals("OTHER")) continue;
            if (perType.merge(d.type(), 1, Integer::sum) <= 2) picks.add(d);
            if (picks.size() == 6) break;
        }
        return picks.stream().map(ResourceDto::id).toList();
    }

    static boolean matches(Resource r, SearchRequest q) {
        if (q.types() != null && !q.types().isEmpty() && !q.types().contains(r.type())) return false;
        if (q.yearFrom() != null && (r.year() == null || r.year() < q.yearFrom())) return false;
        if (q.yearTo() != null && (r.year() == null || r.year() > q.yearTo())) return false;
        if (q.language() != null && !q.language().isBlank() && !q.language().equalsIgnoreCase(r.language())) return false;
        if (q.access() == SearchRequest.AccessFilter.FREE && !r.accessType().isFree()) return false;
        if (q.access() == SearchRequest.AccessFilter.OPEN
                && !(r.accessType() == AccessType.PUBLIC_DOMAIN || r.accessType() == AccessType.OPEN_ACCESS
                || r.accessType() == AccessType.FREE_VERIFIED_PERMISSION)) return false;
        return true;
    }

    private static Map<String, Object> filterMap(SearchRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("page", r.page());
        if (r.types() != null && !r.types().isEmpty()) m.put("types", r.types().stream().map(Enum::name).sorted().collect(Collectors.toList()));
        if (r.yearFrom() != null) m.put("yearFrom", r.yearFrom());
        if (r.yearTo() != null) m.put("yearTo", r.yearTo());
        if (r.language() != null && !r.language().isBlank()) m.put("language", r.language());
        if (r.access() != null) m.put("access", r.access().name());
        if (r.providers() != null && !r.providers().isEmpty()) m.put("providers", new TreeSet<>(r.providers()));
        return m;
    }

    private static ProviderOutcome outcome(SearchProvider p, Status s, String msg) {
        return new ProviderOutcome(p.id(), p.displayName(), s, 0, 0, msg, false);
    }
}
