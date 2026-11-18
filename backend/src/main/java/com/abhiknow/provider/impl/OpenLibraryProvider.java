package com.abhiknow.provider.impl;

import com.abhiknow.access.AccessClassifier;
import com.abhiknow.access.AccessEvidence;
import com.abhiknow.config.AbhiKnowProperties;
import com.abhiknow.domain.*;
import com.abhiknow.provider.HttpFetcher;
import com.abhiknow.provider.ProviderException;
import com.abhiknow.provider.SearchProvider;
import com.abhiknow.resource.Ids;
import com.abhiknow.resource.Languages;
import com.abhiknow.resource.Text;
import com.abhiknow.search.ParsedQuery;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

import static com.abhiknow.provider.ProviderSupport.*;

/** Open Library search.json. Identified requests are limited to 3 per second; we space calls at 350 ms. */
@Component
public class OpenLibraryProvider implements SearchProvider {
    static final String FIELDS = "key,title,subtitle,author_name,first_publish_year,isbn,ebook_access,ia,language,publisher,subject";
    private final HttpFetcher http;
    private final AbhiKnowProperties props;

    public OpenLibraryProvider(HttpFetcher http, AbhiKnowProperties props) {
        this.http = http;
        this.props = props;
    }

    @Override public String id() { return "openlibrary"; }
    @Override public String displayName() { return "Open Library"; }
    @Override public long minIntervalMs() { return 350; }

    @Override
    public boolean supports(ParsedQuery q) {
        return q.mode() == ParsedQuery.Mode.KEYWORD || q.mode() == ParsedQuery.Mode.ISBN || q.mode() == ParsedQuery.Mode.AUTHOR;
    }

    @Override
    public List<ProviderRecord> search(ParsedQuery q, int page, int pageSize, Duration timeout) throws ProviderException {
        StringBuilder url = new StringBuilder(props.urls().openlibrary()).append("/search.json?");
        switch (q.mode()) {
            case ISBN -> url.append("q=").append(enc("isbn:" + q.isbn()));
            case AUTHOR -> {
                url.append("author=").append(enc(q.author()));
                if (!q.text().equals(q.author())) url.append("&q=").append(enc(q.text()));
            }
            default -> url.append("q=").append(enc(q.text()));
        }
        url.append("&limit=").append(pageSize).append("&page=").append(page).append("&fields=").append(FIELDS);
        JsonNode root = parseJson(http.get(url.toString(), timeout));
        JsonNode docs = root.get("docs");
        if (docs == null || !docs.isArray()) throw new ProviderException(ProviderException.Kind.PARSE_ERROR, "Unexpected Open Library response shape");
        List<ProviderRecord> out = new ArrayList<>();
        int rank = (page - 1) * pageSize;
        for (JsonNode d : docs) {
            rank++;
            ProviderRecord r = map(d, rank);
            if (r != null) out.add(r);
        }
        return out;
    }

    static ProviderRecord map(JsonNode d, int rank) {
        String key = text(d, "key");
        String title = Text.collapse(text(d, "title"));
        if (key == null || title == null || !key.startsWith("/works/")) return null;
        String ref = key.substring("/works/".length());

        List<Identifier> ids = new ArrayList<>();
        ids.add(new Identifier(IdScheme.OPENLIBRARY, ref));
        Set<String> isbns = new LinkedHashSet<>();
        for (JsonNode i : d.path("isbn")) {
            String n = Ids.isbn13(i.asText());
            if (n != null && isbns.size() < 12) isbns.add(n);
        }
        isbns.forEach(i -> ids.add(new Identifier(IdScheme.ISBN, i)));

        List<String> authors = new ArrayList<>();
        d.path("author_name").forEach(a -> { if (a.isTextual() && authors.size() < 15) authors.add(a.asText().trim()); });
        List<String> subjects = new ArrayList<>();
        d.path("subject").forEach(s -> { if (s.isTextual() && subjects.size() < 8) subjects.add(s.asText().trim()); });

        // Open Library lists every edition language; only claim a language when there is exactly one.
        String language = d.path("language").isArray() && d.path("language").size() == 1
                ? Languages.normalize(d.path("language").get(0).asText()) : null;
        String publisher = d.path("publisher").isArray() && d.path("publisher").size() > 0 ? d.path("publisher").get(0).asText() : null;

        AccessEvidence ev = AccessClassifier.openLibrary(text(d, "ebook_access"));
        List<Location> locs = new ArrayList<>();
        Location page = location("openlibrary", "https://openlibrary.org" + key, "LANDING", "html", ev, null);
        if (page != null) locs.add(page);
        String access = text(d, "ebook_access");
        if (access != null && (access.equals("public") || access.equals("borrowable"))) {
            for (JsonNode ia : d.path("ia")) {
                String id = ia.asText();
                if (!id.isBlank() && !id.startsWith("isbn_") && id.matches("[A-Za-z0-9._\\-]+")) {
                    Location read = location("openlibrary", "https://archive.org/details/" + id,
                            access.equals("public") ? "READ_ONLINE" : "BORROW", "html", ev, null);
                    if (read != null) locs.add(read);
                    break;
                }
            }
        }
        return new ProviderRecord("openlibrary", ref, rank, title, Text.collapse(text(d, "subtitle")), null, authors, publisher,
                intVal(d, "first_publish_year"), language, ResourceType.BOOK, ids, List.of(), locs, subjects, null);
    }
}
