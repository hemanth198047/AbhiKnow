package com.abhiknow.provider.impl;

import com.abhiknow.access.AccessClassifier;
import com.abhiknow.access.AccessEvidence;
import com.abhiknow.config.AbhiKnowProperties;
import com.abhiknow.domain.*;
import com.abhiknow.provider.HttpFetcher;
import com.abhiknow.provider.ProviderException;
import com.abhiknow.provider.SearchProvider;
import com.abhiknow.resource.Languages;
import com.abhiknow.resource.Text;
import com.abhiknow.search.ParsedQuery;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

import static com.abhiknow.provider.ProviderSupport.*;

/** Project Gutenberg catalogue via the third-party Gutendex API. Public-domain status is Gutenberg's US determination. */
@Component
public class GutendexProvider implements SearchProvider {
    private final HttpFetcher http;
    private final AbhiKnowProperties props;

    public GutendexProvider(HttpFetcher http, AbhiKnowProperties props) {
        this.http = http;
        this.props = props;
    }

    @Override public String id() { return "gutenberg"; }
    @Override public String displayName() { return "Project Gutenberg"; }
    @Override public long minIntervalMs() { return 500; }

    @Override
    public boolean supports(ParsedQuery q) {
        return q.mode() == ParsedQuery.Mode.KEYWORD || q.mode() == ParsedQuery.Mode.AUTHOR;
    }

    @Override
    public List<ProviderRecord> search(ParsedQuery q, int page, int pageSize, Duration timeout) throws ProviderException {
        String term = q.mode() == ParsedQuery.Mode.AUTHOR ? q.author() : q.text();
        // Gutendex pages are fixed at 32 items; we trim to pageSize below.
        String url = props.urls().gutendex() + "/books/?search=" + enc(term) + "&page=" + page;
        JsonNode root = parseJson(http.get(url, timeout));
        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) throw new ProviderException(ProviderException.Kind.PARSE_ERROR, "Unexpected Gutendex response shape");
        List<ProviderRecord> out = new ArrayList<>();
        int rank = 0;
        for (JsonNode b : results) {
            if (++rank > pageSize) break;
            ProviderRecord r = map(b, rank + (page - 1) * pageSize);
            if (r != null) out.add(r);
        }
        return out;
    }

    static ProviderRecord map(JsonNode b, int rank) {
        JsonNode idNode = b.get("id");
        String title = Text.collapse(text(b, "title"));
        if (idNode == null || !idNode.isInt() || title == null) return null;
        String ref = String.valueOf(idNode.asInt());

        List<String> authors = new ArrayList<>();
        for (JsonNode a : b.path("authors")) {
            String n = Text.displayName(text(a, "name"));
            if (n != null && authors.size() < 10) authors.add(n);
        }
        Boolean copyright = b.hasNonNull("copyright") && b.get("copyright").isBoolean() ? b.get("copyright").asBoolean() : null;
        AccessEvidence ev = AccessClassifier.gutenberg(copyright);

        List<Location> locs = new ArrayList<>();
        Location page = location("gutenberg", "https://www.gutenberg.org/ebooks/" + ref, "LANDING", "html", ev, null);
        if (page != null) locs.add(page);
        Map<String, String[]> wanted = new LinkedHashMap<>();
        wanted.put("application/epub+zip", new String[]{"EPUB", "epub"});
        wanted.put("text/html", new String[]{"HTML", "html"});
        wanted.put("text/plain", new String[]{"TXT", "txt"});
        wanted.put("application/pdf", new String[]{"PDF", "pdf"});
        JsonNode formats = b.path("formats");
        for (Map.Entry<String, String[]> w : wanted.entrySet()) {
            String url = null;
            Iterator<Map.Entry<String, JsonNode>> it = formats.fields();
            while (it.hasNext() && url == null) {
                Map.Entry<String, JsonNode> f = it.next();
                if (f.getKey().startsWith(w.getKey()) && f.getValue().isTextual()) url = f.getValue().asText();
            }
            Location l = location("gutenberg", url, w.getValue()[0], w.getValue()[1], ev, null);
            if (l != null) locs.add(l);
        }

        List<String> subjects = new ArrayList<>();
        b.path("subjects").forEach(s -> { if (s.isTextual() && subjects.size() < 8) subjects.add(s.asText()); });
        String language = b.path("languages").isArray() && b.path("languages").size() == 1
                ? Languages.normalize(b.path("languages").get(0).asText()) : null;
        return new ProviderRecord("gutenberg", ref, rank, title, null, null, authors, "Project Gutenberg", null, language,
                ResourceType.BOOK, List.of(new Identifier(IdScheme.GUTENBERG, ref)), List.of(), locs, subjects, null);
    }
}
