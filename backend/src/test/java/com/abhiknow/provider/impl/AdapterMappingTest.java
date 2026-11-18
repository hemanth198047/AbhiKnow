package com.abhiknow.provider.impl;

import com.abhiknow.domain.*;
import com.abhiknow.provider.ProviderException;
import com.abhiknow.provider.ProviderSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.abhiknow.support.Fixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mapping tests. crossref_live / arxiv_live / openlibrary_live are real responses captured from the providers.
 * openalex_constructed / gutendex_constructed / crossref_constructed are hand-written from the documented schemas
 * (OpenAlex and Gutendex were unreachable when this was written) and are NOT evidence of live behaviour.
 */
class AdapterMappingTest {
    private static JsonNode json(String f) throws ProviderException { return ProviderSupport.parseJson(read(f)); }

    // ---------- Open Library (live fixture) ----------
    @Test void openLibraryLiveFixture() throws Exception {
        JsonNode docs = json("openlibrary_live.json").get("docs");
        assertThat(docs.size()).isGreaterThan(0);
        for (int i = 0; i < docs.size(); i++) {
            ProviderRecord r = OpenLibraryProvider.map(docs.get(i), i + 1);
            assertThat(r).isNotNull();
            assertThat(r.type()).isEqualTo(ResourceType.BOOK);
            assertThat(r.locations().get(0).url()).startsWith("https://openlibrary.org/works/OL");
            assertThat(r.identifiers()).extracting(Identifier::scheme).contains(IdScheme.OPENLIBRARY);
            r.identifiers().stream().filter(x -> x.scheme() == IdScheme.ISBN).forEach(x -> assertThat(x.value()).matches("\\d{13}"));
        }
        ProviderRecord first = OpenLibraryProvider.map(docs.get(0), 1);
        assertThat(first.title()).isEqualTo("Artificial Intelligence");
        assertThat(first.authors()).contains("Stuart J. Russell");
        assertThat(first.locations().get(0).accessType()).isEqualTo(AccessType.RESTRICTED);
        ProviderRecord second = OpenLibraryProvider.map(docs.get(1), 2);
        assertThat(second.locations().get(0).accessType()).isEqualTo(AccessType.LIBRARY_BORROWING);
        assertThat(second.locations().get(0).canDownload()).isEqualTo(Permission.NO);
    }

    // ---------- arXiv (live fixture) ----------
    @Test void arxivLiveFixture() throws Exception {
        List<ProviderRecord> recs = ArxivProvider.parse(read("arxiv_live.xml"), 0);
        assertThat(recs).hasSize(3);
        ProviderRecord r = recs.get(0);
        assertThat(r.title()).isEqualTo("The Modern Mathematics of Deep Learning");
        assertThat(r.type()).isEqualTo(ResourceType.PREPRINT);
        assertThat(r.providerRef()).isEqualTo("2105.04026");
        assertThat(r.year()).isEqualTo(2021);
        assertThat(r.authors()).first().isEqualTo("Julius Berner");
        assertThat(r.identifiers()).contains(new Identifier(IdScheme.DOI, "10.48550/arxiv.2105.04026"));
        assertThat(r.locations()).extracting(Location::kind).containsExactly("LANDING", "PDF");
        assertThat(r.locations()).allSatisfy(l -> assertThat(l.accessType()).isEqualTo(AccessType.FREE_TO_READ_ONLINE));
        assertThat(r.rank()).isEqualTo(1);
        assertThat(recs.get(2).rank()).isEqualTo(3);
    }

    @Test void arxivRejectsXmlWithDoctype() {
        String evil = "<?xml version=\"1.0\"?><!DOCTYPE feed [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><feed xmlns=\"http://www.w3.org/2005/Atom\"><entry><title>&x;</title></entry></feed>";
        assertThatThrownBy(() -> ArxivProvider.parse(evil, 0)).isInstanceOf(ProviderException.class)
                .extracting(e -> ((ProviderException) e).kind()).isEqualTo(ProviderException.Kind.PARSE_ERROR);
    }

    @Test void arxivErrorFeedYieldsNoRecords() throws Exception {
        String err = "<feed xmlns=\"http://www.w3.org/2005/Atom\"><entry><id>http://arxiv.org/api/errors#x</id><title>Error</title></entry></feed>";
        assertThat(ArxivProvider.parse(err, 0)).isEmpty();
    }

    @Test void arxivQueryTranslation() {
        var pq = new com.abhiknow.search.QueryParser().parse("graph neural networks");
        assertThat(ArxivProvider.buildQuery(pq)).isEqualTo("all:graph AND all:neural AND all:networks");
        var author = new com.abhiknow.search.QueryParser().parse("author:\"Geoffrey Hinton\"");
        assertThat(ArxivProvider.buildQuery(author)).isEqualTo("au:\"Geoffrey Hinton\"");
    }

    // ---------- Crossref ----------
    @Test void crossrefLiveFixtureChaptersDoNotCarryIsbn() throws Exception {
        JsonNode items = json("crossref_live.json").path("message").path("items");
        ProviderRecord r = CrossrefProvider.map(items.get(0), 1);
        assertThat(r.type()).isEqualTo(ResourceType.OTHER);
        assertThat(r.identifiers()).extracting(Identifier::scheme).containsOnly(IdScheme.DOI);
        assertThat(r.year()).isEqualTo(2018);
        assertThat(r.locations().get(0).accessType()).isEqualTo(AccessType.UNKNOWN);
    }

    @Test void crossrefConstructedFixture() throws Exception {
        JsonNode items = json("crossref_constructed.json").path("message").path("items");
        ProviderRecord closed = CrossrefProvider.map(items.get(0), 1);
        assertThat(closed.type()).isEqualTo(ResourceType.PAPER);
        assertThat(closed.authors()).containsExactly("Yann LeCun", "Yoshua Bengio");
        assertThat(closed.year()).isEqualTo(2015);
        assertThat(closed.description()).isEqualTo("Deep learning allows computational models & more.");
        assertThat(closed.locations().get(0).accessType()).isEqualTo(AccessType.UNKNOWN);
        assertThat(closed.citedBy()).isEqualTo(50000);

        ProviderRecord open = CrossrefProvider.map(items.get(1), 2);
        assertThat(open.locations().get(0).accessType()).isEqualTo(AccessType.OPEN_ACCESS);
        assertThat(open.locations().get(0).license()).contains("creativecommons.org");
        assertThat(open.authors()).containsExactly("Open Consortium");

        ProviderRecord book = CrossrefProvider.map(items.get(2), 3);
        assertThat(book.type()).isEqualTo(ResourceType.BOOK);
        assertThat(book.identifiers()).contains(new Identifier(IdScheme.ISBN, "9780306406157"));
    }

    // ---------- OpenAlex (constructed) ----------
    @Test void openAlexConstructedFixture() throws Exception {
        JsonNode results = json("openalex_constructed.json").get("results");
        ProviderRecord oa = OpenAlexProvider.map(results.get(0), 1);
        assertThat(oa.type()).isEqualTo(ResourceType.PREPRINT);
        assertThat(oa.providerRef()).isEqualTo("W2626778328");
        assertThat(oa.identifiers()).contains(new Identifier(IdScheme.DOI, "10.48550/arxiv.1706.03762"));
        assertThat(oa.description()).isEqualTo("The dominant sequence models");
        assertThat(oa.locations()).extracting(Location::kind).containsExactly("PDF", "LANDING");
        assertThat(oa.locations().get(0).accessType()).isEqualTo(AccessType.OPEN_ACCESS);
        assertThat(oa.locations().get(0).canRedistribute()).isEqualTo(Permission.YES);

        ProviderRecord closed = OpenAlexProvider.map(results.get(1), 2);
        assertThat(closed.type()).isEqualTo(ResourceType.PAPER);
        assertThat(closed.locations()).hasSize(1);
        assertThat(closed.locations().get(0).accessType()).isEqualTo(AccessType.UNKNOWN);
        assertThat(closed.description()).isNull();
    }

    // ---------- Gutendex (constructed) ----------
    @Test void gutendexConstructedFixture() throws Exception {
        JsonNode results = json("gutendex_constructed.json").get("results");
        ProviderRecord alice = GutendexProvider.map(results.get(0), 1);
        assertThat(alice.authors()).containsExactly("Lewis Carroll");
        assertThat(alice.language()).isEqualTo("en");
        assertThat(alice.locations()).extracting(Location::kind).containsExactly("LANDING", "EPUB", "HTML", "TXT");
        assertThat(alice.locations()).allSatisfy(l -> {
            assertThat(l.accessType()).isEqualTo(AccessType.PUBLIC_DOMAIN);
            assertThat(l.url()).startsWith("https://www.gutenberg.org/");
        });

        ProviderRecord unknown = GutendexProvider.map(results.get(1), 2);
        assertThat(unknown.language()).isNull();
        assertThat(unknown.locations()).hasSize(1);          // javascript: URL dropped
        assertThat(unknown.locations().get(0).accessType()).isEqualTo(AccessType.UNKNOWN);
    }
}
