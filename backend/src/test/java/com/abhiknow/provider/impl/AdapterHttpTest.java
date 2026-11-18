package com.abhiknow.provider.impl;

import com.abhiknow.config.AbhiKnowProperties;
import com.abhiknow.domain.ProviderRecord;
import com.abhiknow.provider.HttpFetcher;
import com.abhiknow.provider.ProviderException;
import com.abhiknow.search.QueryParser;
import com.abhiknow.support.StubServer;
import com.abhiknow.support.TestProps;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.abhiknow.support.Fixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Adapters against a local stub: verifies query translation, identification headers and HTTP error mapping. */
class AdapterHttpTest {
    private final QueryParser parser = new QueryParser();

    private AbhiKnowProperties props(String url) {
        return TestProps.of(2000, 0, 10, new AbhiKnowProperties.Urls(url, url, url, url, url));
    }

    @Test void openLibraryRequestIsIdentifiedAndTranslated() throws Exception {
        try (StubServer s = new StubServer(p -> StubServer.Response.json(read("openlibrary_live.json")))) {
            AbhiKnowProperties pr = props(s.url());
            OpenLibraryProvider p = new OpenLibraryProvider(new HttpFetcher(pr), pr);
            List<ProviderRecord> out = p.search(parser.parse("artificial intelligence"), 1, 15, Duration.ofSeconds(2));
            assertThat(out).isNotEmpty();
            StubServer.Seen seen = s.requests.get(0);
            assertThat(seen.pathAndQuery()).startsWith("/search.json?q=artificial+intelligence").contains("limit=15").contains("fields=");
            assertThat(seen.userAgent()).contains("AbhiKnow").contains("mailto:tester@example.org");
        }
    }

    @Test void openLibraryIsbnQuery() throws Exception {
        try (StubServer s = new StubServer(p -> StubServer.Response.json("{\"docs\":[]}"))) {
            AbhiKnowProperties pr = props(s.url());
            new OpenLibraryProvider(new HttpFetcher(pr), pr).search(parser.parse("978-0-306-40615-7"), 1, 15, Duration.ofSeconds(2));
            assertThat(s.requests.get(0).pathAndQuery()).contains("q=isbn%3A9780306406157");
        }
    }

    @Test void openAlexSendsKeyOnlyWhenConfigured() throws Exception {
        try (StubServer s = new StubServer(p -> StubServer.Response.json(read("openalex_constructed.json")))) {
            AbhiKnowProperties pr = new AbhiKnowProperties("tester@example.org", 2000, 15, 8, 0, 10, 1000, "KEY123", "x",
                    new AbhiKnowProperties.Urls(s.url(), s.url(), s.url(), s.url(), s.url()));
            OpenAlexProvider p = new OpenAlexProvider(new HttpFetcher(pr), pr);
            assertThat(p.search(parser.parse("attention"), 1, 15, Duration.ofSeconds(2))).hasSize(2);
            assertThat(s.requests.get(0).pathAndQuery()).contains("api_key=KEY123").contains("search=attention").contains("mailto=tester%40example.org");

            AbhiKnowProperties nokey = props(s.url());
            new OpenAlexProvider(new HttpFetcher(nokey), nokey).search(parser.parse("attention"), 1, 15, Duration.ofSeconds(2));
            assertThat(s.requests.get(1).pathAndQuery()).doesNotContain("api_key");
        }
    }

    @Test void openAlexDoiLookupUsesFilter() throws Exception {
        try (StubServer s = new StubServer(p -> StubServer.Response.json("{\"results\":[]}"))) {
            AbhiKnowProperties pr = props(s.url());
            new OpenAlexProvider(new HttpFetcher(pr), pr).search(parser.parse("10.1038/nature14539"), 1, 15, Duration.ofSeconds(2));
            assertThat(s.requests.get(0).pathAndQuery()).contains("filter=doi:https%3A%2F%2Fdoi.org%2F10.1038%2Fnature14539");
        }
    }

    @Test void crossrefDoiLookupAndUnknownDoi() throws Exception {
        try (StubServer s = new StubServer(p -> StubServer.Response.status(404))) {
            AbhiKnowProperties pr = props(s.url());
            CrossrefProvider c = new CrossrefProvider(new HttpFetcher(pr), pr);
            assertThat(c.search(parser.parse("10.9999/does-not-exist"), 1, 15, Duration.ofSeconds(2))).isEmpty();
            assertThat(s.requests.get(0).pathAndQuery()).startsWith("/works/10.9999/does-not-exist").doesNotContain("select=");
        }
    }

    /** Regression: Crossref returns 400 for select on /works/{doi}; that must surface as an error, not as "not found". */
    @Test void crossrefBadRequestIsNotSwallowedAsUnknownDoi() {
        try (StubServer s = new StubServer(p -> StubServer.Response.status(400))) {
            AbhiKnowProperties pr = props(s.url());
            CrossrefProvider c = new CrossrefProvider(new HttpFetcher(pr), pr);
            assertThatThrownBy(() -> c.search(parser.parse("10.1038/nature14539"), 1, 15, Duration.ofSeconds(2)))
                    .isInstanceOf(ProviderException.class).extracting(e -> ((ProviderException) e).kind()).isEqualTo(ProviderException.Kind.HTTP_ERROR);
        }
    }

    @Test void crossrefDoiLookupParsesSingleWorkMessage() throws Exception {
        String single = "{\"status\":\"ok\",\"message\":{\"DOI\":\"10.1038/nature14539\",\"title\":[\"Deep learning\"],\"type\":\"journal-article\",\"issued\":{\"date-parts\":[[2015]]}}}";
        try (StubServer s = new StubServer(p -> StubServer.Response.json(single))) {
            AbhiKnowProperties pr = props(s.url());
            var out = new CrossrefProvider(new HttpFetcher(pr), pr).search(parser.parse("10.1038/nature14539"), 1, 15, Duration.ofSeconds(2));
            assertThat(out).hasSize(1);
            assertThat(out.get(0).title()).isEqualTo("Deep learning");
        }
    }

    @Test void httpErrorsMapToProviderExceptionKinds() {
        for (var c : List.of(new int[]{429, 0}, new int[]{503, 1}, new int[]{400, 2}, new int[]{404, 3})) {
            try (StubServer s = new StubServer(p -> StubServer.Response.status(c[0]))) {
                AbhiKnowProperties pr = props(s.url());
                GutendexProvider g = new GutendexProvider(new HttpFetcher(pr), pr);
                var expected = new ProviderException.Kind[]{ProviderException.Kind.RATE_LIMITED, ProviderException.Kind.UNAVAILABLE, ProviderException.Kind.HTTP_ERROR, ProviderException.Kind.NOT_FOUND}[c[1]];
                assertThatThrownBy(() -> g.search(parser.parse("x books"), 1, 15, Duration.ofSeconds(2)))
                        .isInstanceOf(ProviderException.class).extracting(e -> ((ProviderException) e).kind()).isEqualTo(expected);
            }
        }
    }

    @Test void malformedJsonIsParseError() {
        try (StubServer s = new StubServer(p -> StubServer.Response.json("<html>not json</html>"))) {
            AbhiKnowProperties pr = props(s.url());
            assertThatThrownBy(() -> new GutendexProvider(new HttpFetcher(pr), pr).search(parser.parse("x books"), 1, 15, Duration.ofSeconds(2)))
                    .isInstanceOf(ProviderException.class).extracting(e -> ((ProviderException) e).kind()).isEqualTo(ProviderException.Kind.PARSE_ERROR);
        }
    }

    @Test void connectionRefusedIsUnavailableNotCrash() {
        AbhiKnowProperties pr = props("http://127.0.0.1:1");
        assertThatThrownBy(() -> new GutendexProvider(new HttpFetcher(pr), pr).search(parser.parse("x books"), 1, 15, Duration.ofSeconds(2)))
                .isInstanceOf(ProviderException.class).extracting(e -> ((ProviderException) e).kind()).isEqualTo(ProviderException.Kind.UNAVAILABLE);
    }
}
