package com.abhiknow.ranking;

import com.abhiknow.domain.*;
import com.abhiknow.search.ParsedQuery;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RankerTest {
    private final Ranker ranker = new Ranker(Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC));
    private final ParsedQuery q = new ParsedQuery("deep learning", "deep learning", ParsedQuery.Mode.KEYWORD, List.of(), null, null, null, null);

    private Resource res(String key, String title, AccessType access, int rank, int year, Integer cited, List<SourceRef> extra) {
        List<SourceRef> sources = new java.util.ArrayList<>(List.of(new SourceRef("openalex", key, rank)));
        sources.addAll(extra);
        return new Resource(null, key, title, null, "abstract", ResourceType.PAPER, "en", null, year, List.of("A Author"),
                List.of(), List.of(), sources, List.of(), cited, access, List.of(), 0, List.of());
    }

    @Test void titleMatchAndFreeAccessBeatPoorMatch() {
        Resource good = res("a", "Deep Learning", AccessType.OPEN_ACCESS, 1, 2024, null, List.of());
        Resource poor = res("b", "Unrelated Topic", AccessType.UNKNOWN, 1, 2024, null, List.of());
        List<Resource> out = ranker.rank(List.of(poor, good), q, 15);
        assertThat(out.get(0).key()).isEqualTo("a");
        assertThat(out.get(0).reasons()).anyMatch(s -> s.contains("Title matches 2 of 2"));
    }

    @Test void multipleSourcesCorroborate() {
        Resource single = res("a", "Deep Learning", AccessType.UNKNOWN, 2, 2024, null, List.of());
        Resource multi = res("b", "Deep Learning", AccessType.UNKNOWN, 2, 2024, null,
                List.of(new SourceRef("crossref", "x", 2), new SourceRef("arxiv", "y", 2)));
        assertThat(ranker.score(multi, List.of("deep", "learning"), 15).score()).isGreaterThan(ranker.score(single, List.of("deep", "learning"), 15).score());
        assertThat(ranker.score(multi, List.of("deep"), 15).reasons()).anyMatch(s -> s.contains("3 independent sources"));
    }

    @Test void citationCountDoesNotChangeScore() {
        Resource none = res("a", "Deep Learning", AccessType.UNKNOWN, 3, 2020, null, List.of());
        Resource famous = res("b", "Deep Learning", AccessType.UNKNOWN, 3, 2020, 900000, List.of());
        assertThat(ranker.score(famous, List.of("deep", "learning"), 15).score()).isEqualTo(ranker.score(none, List.of("deep", "learning"), 15).score());
        assertThat(ranker.score(famous, List.of("deep"), 15).reasons()).anyMatch(s -> s.contains("not used for ranking"));
    }

    @Test void booksAreNotPenalisedForAge() {
        Resource oldBook = new Resource(null, "k", "Deep Learning", null, null, ResourceType.BOOK, "en", null, 1950, List.of("A B"),
                List.of(), List.of(), List.of(new SourceRef("openlibrary", "x", 1)), List.of(), null, AccessType.PUBLIC_DOMAIN, List.of(), 0, List.of());
        Resource newBook = new Resource(null, "k2", "Deep Learning", null, null, ResourceType.BOOK, "en", null, 2025, List.of("A B"),
                List.of(), List.of(), List.of(new SourceRef("openlibrary", "y", 1)), List.of(), null, AccessType.PUBLIC_DOMAIN, List.of(), 0, List.of());
        assertThat(ranker.score(oldBook, List.of("deep"), 15).score()).isGreaterThanOrEqualTo(ranker.score(newBook, List.of("deep"), 15).score() - 0.0001);
    }

    @Test void scoreIsBoundedAndDeterministic() {
        Resource r = res("a", "Deep Learning", AccessType.PUBLIC_DOMAIN, 1, 2026, null, List.of());
        double s = ranker.score(r, List.of("deep", "learning"), 15).score();
        assertThat(s).isBetween(0.0, 1.0).isEqualTo(ranker.score(r, List.of("deep", "learning"), 15).score());
    }
}
