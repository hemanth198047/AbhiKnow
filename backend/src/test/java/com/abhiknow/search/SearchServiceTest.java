package com.abhiknow.search;

import com.abhiknow.domain.*;
import com.abhiknow.provider.*;
import com.abhiknow.provider.ProviderOutcome.Status;
import com.abhiknow.ranking.Ranker;
import com.abhiknow.resource.Deduplicator;
import com.abhiknow.support.InMemoryStore;
import com.abhiknow.support.TestProps;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static com.abhiknow.support.Fixtures.location;
import static org.assertj.core.api.Assertions.assertThat;

class SearchServiceTest {
    static final class Fake implements SearchProvider {
        final String id; final java.util.function.Supplier<List<ProviderRecord>> body; final boolean doi;
        Fake(String id, java.util.function.Supplier<List<ProviderRecord>> body) { this(id, body, true); }
        Fake(String id, java.util.function.Supplier<List<ProviderRecord>> body, boolean doi) { this.id = id; this.body = body; this.doi = doi; }
        public String id() { return id; }
        public String displayName() { return id; }
        public boolean supports(ParsedQuery q) { return doi || q.mode() != ParsedQuery.Mode.DOI; }
        public List<ProviderRecord> search(ParsedQuery q, int p, int s, Duration t) throws ProviderException { return body.get(); }
    }

    private static ProviderRecord rec(String provider, String ref, int rank, String title, ResourceType type, int year,
                                      String author, AccessType access, Identifier... ids) {
        return new ProviderRecord(provider, ref, rank, title, null, "desc", List.of(author), null, year, "en", type, List.of(ids),
                List.of(), List.of(location(provider, "https://" + provider + ".example/" + ref, access)), List.of(), null);
    }

    private SearchService service(InMemoryStore store, SearchProvider... providers) {
        var props = TestProps.of(300, 0, 10);
        return new SearchService(List.of(providers), new ProviderRuntime(props), new QueryParser(), new Deduplicator(), new Ranker(), store, props);
    }

    private static SearchRequest req(String q) {
        return new SearchRequest(q, 1, Set.of(), null, null, null, SearchRequest.AccessFilter.ANY, Set.of());
    }

    @Test void oneProviderFailingAndOneTimingOutDoNotStopTheSearch() {
        InMemoryStore store = new InMemoryStore().trust("good", "PENDING_REVIEW").trust("broken", "PENDING_REVIEW").trust("slow", "PENDING_REVIEW");
        SearchService svc = service(store,
                new Fake("good", () -> List.of(rec("good", "1", 1, "Deep Learning", ResourceType.BOOK, 2016, "Ian Goodfellow", AccessType.OPEN_ACCESS))),
                new Fake("broken", () -> { throw new IllegalStateException("provider exploded"); }),
                new Fake("slow", () -> { try { Thread.sleep(10_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } return List.of(); }));
        SearchResponse r = svc.search(req("deep learning"));
        assertThat(r.results()).hasSize(1);
        assertThat(r.results().get(0).title()).isEqualTo("Deep Learning");
        assertThat(r.results().get(0).id()).isNotNull();
        assertThat(r.providers()).extracting(ProviderOutcome::providerId, ProviderOutcome::status)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("good", Status.OK),
                        org.assertj.core.groups.Tuple.tuple("broken", Status.ERROR),
                        org.assertj.core.groups.Tuple.tuple("slow", Status.TIMEOUT));
        assertThat(r.coverage()).contains("Searched 1 of 3").contains("not exhaustive");
        assertThat(store.searches).isEqualTo(1);
    }

    @Test void duplicatesAcrossProvidersCollapseWithProvenance() {
        Identifier doi = new Identifier(IdScheme.DOI, "10.1038/nature14539");
        InMemoryStore store = new InMemoryStore().trust("a", "PENDING_REVIEW").trust("b", "PENDING_REVIEW");
        SearchService svc = service(store,
                new Fake("a", () -> List.of(rec("a", "A1", 1, "Deep learning", ResourceType.PAPER, 2015, "Yann LeCun", AccessType.UNKNOWN, doi))),
                new Fake("b", () -> List.of(rec("b", "B1", 2, "Deep Learning", ResourceType.PAPER, 2015, "Yann LeCun", AccessType.OPEN_ACCESS, doi))));
        SearchResponse r = svc.search(req("deep learning"));
        assertThat(r.results()).hasSize(1);
        ResourceDto d = r.results().get(0);
        assertThat(d.sources()).containsExactlyInAnyOrder("a", "b");
        assertThat(d.accessType()).isEqualTo("OPEN_ACCESS");
        assertThat(d.recommendedAction()).isEqualTo("Read for free");
        assertThat(d.reasons()).anyMatch(s -> s.contains("2 independent sources"));
    }

    @Test void recommendedShortlistKeepsFormatsBalanced() {
        InMemoryStore store = new InMemoryStore().trust("a", "PENDING_REVIEW");
        List<ProviderRecord> recs = new java.util.ArrayList<>();
        for (int i = 1; i <= 6; i++) recs.add(rec("a", "p" + i, i, "Neural Networks Paper " + i, ResourceType.PREPRINT, 2024, "Au" + i + " Thor" + i, AccessType.FREE_TO_READ_ONLINE));
        recs.add(rec("a", "b1", 7, "Neural Networks Book", ResourceType.BOOK, 2000, "Bo Ok", AccessType.LIBRARY_BORROWING));
        SearchResponse r = service(store, new Fake("a", () -> recs)).search(req("neural networks"));
        List<String> types = r.recommendedIds().stream().map(id -> r.results().stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow().type()).toList();
        assertThat(types.stream().filter(t -> t.equals("PREPRINT")).count()).isEqualTo(2);
        assertThat(types).contains("BOOK");
    }

    @Test void blockedAndUnregisteredProvidersAreNeverQueried() {
        int[] calls = {0};
        InMemoryStore store = new InMemoryStore().trust("blocked", "BLOCKED");
        SearchService svc = service(store,
                new Fake("blocked", () -> { calls[0]++; return List.of(); }),
                new Fake("unregistered", () -> { calls[0]++; return List.of(); }));
        SearchResponse r = svc.search(req("anything"));
        assertThat(calls[0]).isZero();
        assertThat(r.providers()).extracting(ProviderOutcome::status).containsOnly(Status.DISABLED);
        assertThat(r.results()).isEmpty();
    }

    @Test void providersThatCannotAnswerAreSkippedNotFailed() {
        InMemoryStore store = new InMemoryStore().trust("kw", "PENDING_REVIEW").trust("any", "PENDING_REVIEW");
        SearchService svc = service(store, new Fake("kw", List::of, false), new Fake("any", List::of));
        SearchResponse r = svc.search(req("10.1038/nature14539"));
        assertThat(r.providers()).extracting(ProviderOutcome::providerId, ProviderOutcome::status)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("kw", Status.SKIPPED), org.assertj.core.groups.Tuple.tuple("any", Status.OK));
    }

    @Test void noResultsIsAnHonestEmptyResponse() {
        InMemoryStore store = new InMemoryStore().trust("a", "PENDING_REVIEW");
        SearchResponse r = svc(store).search(req("zzzz qqqq"));
        assertThat(r.results()).isEmpty();
        assertThat(r.providers()).hasSize(1);
        assertThat(r.coverage()).contains("not exhaustive");
    }

    private SearchService svc(InMemoryStore store) { return service(store, new Fake("a", List::of)); }

    @Test void filtersApplyAfterRanking() {
        InMemoryStore store = new InMemoryStore().trust("a", "PENDING_REVIEW");
        SearchService svc = service(store, new Fake("a", () -> List.of(
                rec("a", "1", 1, "Neural Networks Old", ResourceType.BOOK, 1999, "X One", AccessType.PUBLIC_DOMAIN),
                rec("a", "2", 2, "Neural Networks New", ResourceType.PAPER, 2024, "Y Two", AccessType.UNKNOWN),
                rec("a", "3", 3, "Neural Networks Borrow", ResourceType.BOOK, 2020, "Z Three", AccessType.LIBRARY_BORROWING))));
        assertThat(svc.search(new SearchRequest("neural networks", 1, Set.of(ResourceType.BOOK), null, null, null, SearchRequest.AccessFilter.ANY, Set.of())).results()).hasSize(2);
        assertThat(svc.search(new SearchRequest("neural networks", 1, Set.of(), 2010, null, null, SearchRequest.AccessFilter.ANY, Set.of())).results()).hasSize(2);
        SearchResponse free = svc.search(new SearchRequest("neural networks", 1, Set.of(), null, null, null, SearchRequest.AccessFilter.FREE, Set.of()));
        assertThat(free.results()).extracting(ResourceDto::title).containsExactly("Neural Networks Old");
        assertThat(free.totalBeforeFilters()).isEqualTo(3);   // honest about what the filter removed
    }
}
