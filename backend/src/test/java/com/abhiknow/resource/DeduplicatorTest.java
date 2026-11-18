package com.abhiknow.resource;

import com.abhiknow.domain.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.abhiknow.support.Fixtures.record;
import static org.assertj.core.api.Assertions.assertThat;

class DeduplicatorTest {
    private final Deduplicator d = new Deduplicator();

    private static Identifier doi(String v) { return new Identifier(IdScheme.DOI, v); }
    private static Identifier isbn(String v) { return new Identifier(IdScheme.ISBN, v); }

    @Test void sameDoiAcrossProvidersMerges() {
        var a = record("openalex", "W1", 1, "Deep learning", ResourceType.PAPER, 2015, List.of("Yann LeCun"), List.of(doi("10.1038/nature14539")));
        var b = record("crossref", "10.1038/nature14539", 2, "Deep Learning", ResourceType.PAPER, 2015, List.of("Yann LeCun"), List.of(doi("10.1038/nature14539")));
        List<Resource> out = d.merge(List.of(a, b));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).sources()).extracting(SourceRef::providerId).containsExactlyInAnyOrder("openalex", "crossref");
    }

    @Test void sameIsbnMergesBooks() {
        var a = record("openlibrary", "OL1W", 1, "Artificial Intelligence", ResourceType.BOOK, 1994, List.of("Stuart Russell"), List.of(isbn("9780136042594")));
        var b = record("crossref", "10.1/x", 1, "Artificial Intelligence: A Modern Approach", ResourceType.BOOK, 2010, List.of("Stuart Russell"), List.of(isbn("9780136042594")));
        assertThat(d.merge(List.of(a, b))).hasSize(1);
    }

    @Test void fuzzyTitleAndAuthorMergeWhenYearUnknown() {
        var ol = record("openlibrary", "OL2W", 1, "Alice's adventures in Wonderland", ResourceType.BOOK, 1865, List.of("Lewis Carroll"), List.of());
        var pg = record("gutenberg", "11", 1, "Alice's Adventures in Wonderland", ResourceType.BOOK, null, List.of("Lewis Carroll"), List.of());
        assertThat(d.merge(List.of(ol, pg))).hasSize(1);
    }

    @Test void differentAuthorsDoNotMerge() {
        var a = record("openlibrary", "1", 1, "Introduction to Algorithms", ResourceType.BOOK, 2009, List.of("Thomas Cormen"), List.of());
        var b = record("openlibrary", "2", 2, "Introduction to Algorithms", ResourceType.BOOK, 2009, List.of("Someone Else"), List.of());
        assertThat(d.merge(List.of(a, b))).hasSize(2);
    }

    @Test void missingAuthorsNeverFuzzyMerge() {
        var a = record("openalex", "1", 1, "Untitled report on climate models", ResourceType.PAPER, 2020, List.of(), List.of());
        var b = record("crossref", "2", 2, "Untitled report on climate models", ResourceType.PAPER, 2020, List.of(), List.of());
        assertThat(d.merge(List.of(a, b))).hasSize(2);
    }

    @Test void booksWithDifferentYearsStayDistinctEditionsAndAreRelated() {
        var a = record("openlibrary", "1", 1, "Artificial Intelligence", ResourceType.BOOK, 1995, List.of("Stuart Russell"), List.of());
        var b = record("crossref", "10.2/y", 2, "Artificial Intelligence", ResourceType.BOOK, 2010, List.of("Stuart Russell"), List.of());
        List<Resource> out = d.merge(List.of(a, b));
        assertThat(out).hasSize(2);
        assertThat(out).allSatisfy(r -> assertThat(r.relations()).extracting(Relation::kind).containsExactly(Relation.EDITION));
    }

    @Test void preprintAndPublishedPaperAreKeptSeparateButRelated() {
        var pre = new ProviderRecord("arxiv", "2105.04026", 1, "The Modern Mathematics of Deep Learning", null, null,
                List.of("Julius Berner"), "arXiv", 2021, "en", ResourceType.PREPRINT,
                List.of(new Identifier(IdScheme.ARXIV, "2105.04026"), doi("10.48550/arxiv.2105.04026")),
                List.of("10.1017/9781009025096.002"), List.of(), List.of(), null);
        var pub = record("crossref", "10.1017/9781009025096.002", 1, "The Modern Mathematics of Deep Learning", ResourceType.PAPER, 2022,
                List.of("Julius Berner"), List.of(doi("10.1017/9781009025096.002")));
        List<Resource> out = d.merge(List.of(pre, pub));
        assertThat(out).hasSize(2);
        Resource preprint = out.stream().filter(r -> r.type() == ResourceType.PREPRINT).findFirst().orElseThrow();
        assertThat(preprint.relations()).extracting(Relation::kind).containsExactly(Relation.PREPRINT_OF);
    }

    @Test void arxivRecordMergesWithOpenAlexPreprintViaArxivDoi() {
        var oa = record("openalex", "W2626778328", 1, "Attention Is All You Need", ResourceType.PREPRINT, 2017,
                List.of("Ashish Vaswani"), List.of(doi("10.48550/arxiv.1706.03762")));
        var ax = record("arxiv", "1706.03762", 1, "Attention Is All You Need", ResourceType.PREPRINT, 2017,
                List.of("Ashish Vaswani"), List.of(new Identifier(IdScheme.ARXIV, "1706.03762"), doi("10.48550/arxiv.1706.03762")));
        assertThat(d.merge(List.of(oa, ax))).hasSize(1);
    }

    @Test void mergedAccessIsBestOfLocations() {
        var a = new ProviderRecord("openalex", "W1", 1, "Same Work", null, null, List.of("A B"), null, 2020, null, ResourceType.PAPER,
                List.of(doi("10.1/z")), List.of(), List.of(com.abhiknow.support.Fixtures.location("openalex", "https://a.example/1", AccessType.UNKNOWN)), List.of(), null);
        var b = new ProviderRecord("crossref", "10.1/z", 1, "Same Work", null, null, List.of("A B"), null, 2020, null, ResourceType.PAPER,
                List.of(doi("10.1/z")), List.of(), List.of(com.abhiknow.support.Fixtures.location("crossref", "https://b.example/2", AccessType.OPEN_ACCESS)), List.of(), null);
        Resource r = d.merge(List.of(a, b)).get(0);
        assertThat(r.accessType()).isEqualTo(AccessType.OPEN_ACCESS);
        assertThat(r.locations()).hasSize(2);
    }
}
