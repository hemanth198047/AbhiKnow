package com.abhiknow.provider.impl;

import com.abhiknow.access.AccessClassifier;
import com.abhiknow.access.AccessEvidence;
import com.abhiknow.config.AbhiKnowProperties;
import com.abhiknow.domain.*;
import com.abhiknow.provider.HttpFetcher;
import com.abhiknow.provider.ProviderException;
import com.abhiknow.provider.SearchProvider;
import com.abhiknow.resource.Ids;
import com.abhiknow.resource.Text;
import com.abhiknow.search.ParsedQuery;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

import static com.abhiknow.provider.ProviderSupport.*;

/** Crossref REST API: DOI metadata. Uses the polite pool via mailto. */
@Component
public class CrossrefProvider implements SearchProvider {
    static final String SELECT = "DOI,title,subtitle,author,issued,type,publisher,container-title,license,abstract,URL,is-referenced-by-count,ISBN";
    private final HttpFetcher http;
    private final AbhiKnowProperties props;

    public CrossrefProvider(HttpFetcher http, AbhiKnowProperties props) {
        this.http = http;
        this.props = props;
    }

    @Override public String id() { return "crossref"; }
    @Override public String displayName() { return "Crossref"; }

    @Override
    public boolean supports(ParsedQuery q) {
        return q.mode() != ParsedQuery.Mode.ARXIV;
    }

    @Override
    public List<ProviderRecord> search(ParsedQuery q, int page, int pageSize, Duration timeout) throws ProviderException {
        String base = props.urls().crossref();
        String mailto = "&mailto=" + enc(props.contactEmail());
        List<JsonNode> items = new ArrayList<>();
        if (q.mode() == ParsedQuery.Mode.DOI) {
            JsonNode root;
            try {
                root = parseJson(http.get(base + "/works/" + enc(q.doi()).replace("%2F", "/") + "?" + mailto.substring(1), timeout)); // this route rejects select (HTTP 400)
            } catch (ProviderException e) {
                if (e.kind() == ProviderException.Kind.NOT_FOUND) return List.of(); // unknown DOI
                throw e;
            }
            JsonNode msg = root.get("message");
            if (msg != null && msg.isObject()) items.add(msg);
        } else {
            StringBuilder url = new StringBuilder(base).append("/works?rows=").append(pageSize)
                    .append("&offset=").append((page - 1) * pageSize).append("&select=").append(SELECT);
            switch (q.mode()) {
                case ISBN -> url.append("&filter=isbn:").append(enc(q.isbn()));
                case AUTHOR -> {
                    url.append("&query.author=").append(enc(q.author()));
                    if (!q.text().equals(q.author())) url.append("&query.bibliographic=").append(enc(q.text()));
                }
                default -> url.append("&query.bibliographic=").append(enc(q.text()));
            }
            url.append(mailto);
            JsonNode root = parseJson(http.get(url.toString(), timeout));
            JsonNode arr = root.path("message").path("items");
            if (!arr.isArray()) throw new ProviderException(ProviderException.Kind.PARSE_ERROR, "Unexpected Crossref response shape");
            arr.forEach(items::add);
        }
        List<ProviderRecord> out = new ArrayList<>();
        int rank = (page - 1) * pageSize;
        for (JsonNode it : items) {
            rank++;
            ProviderRecord r = map(it, rank);
            if (r != null) out.add(r);
        }
        return out;
    }

    static ProviderRecord map(JsonNode w, int rank) {
        String doi = Ids.doi(text(w, "DOI"));
        String title = Text.collapse(text(w.path("title"), "0") != null ? text(w.path("title"), "0") : first(w.path("title")));
        if (doi == null || title == null) return null;
        ResourceType type = type(text(w, "type"));

        List<Identifier> ids = new ArrayList<>();
        ids.add(new Identifier(IdScheme.DOI, doi));
        // ISBNs on a chapter identify the parent book, so only whole books contribute ISBNs.
        if (type == ResourceType.BOOK) {
            for (JsonNode i : w.path("ISBN")) {
                String isbn = Ids.isbn13(i.asText());
                if (isbn != null) ids.add(new Identifier(IdScheme.ISBN, isbn));
            }
        }

        List<String> authors = new ArrayList<>();
        for (JsonNode a : w.path("author")) {
            String name = text(a, "name");
            if (name == null) {
                String g = text(a, "given"), f = text(a, "family");
                name = Text.collapse((g == null ? "" : g) + " " + (f == null ? "" : f));
            }
            if (name != null && authors.size() < 25) authors.add(name);
        }

        List<String> licenses = new ArrayList<>();
        for (JsonNode l : w.path("license")) {
            String u = text(l, "URL");
            if (u != null) licenses.add(u);
        }
        AccessEvidence ev = AccessClassifier.crossref(licenses);
        String openLicense = licenses.stream().filter(AccessClassifier::isOpenLicense).findFirst().orElse(null);

        List<Location> locs = new ArrayList<>();
        Location landing = location("crossref", "https://doi.org/" + doi, "LANDING", "html", ev, openLicense);
        if (landing != null) locs.add(landing);

        Integer year = null;
        JsonNode parts = w.path("issued").path("date-parts").path(0);
        if (parts.isArray() && parts.size() > 0 && parts.get(0).isInt()) year = parts.get(0).asInt();

        String subtitle = Text.collapse(first(w.path("subtitle")));
        return new ProviderRecord("crossref", doi, rank, title, subtitle, Text.truncate(Text.stripMarkup(text(w, "abstract")), 1500),
                authors, text(w, "publisher"), year, null, type, ids, List.of(), locs, List.of(),
                intVal(w, "is-referenced-by-count"));
    }

    static ResourceType type(String t) {
        if (t == null) return ResourceType.OTHER;
        return switch (t) {
            case "journal-article", "proceedings-article", "posted-content-article" -> ResourceType.PAPER;
            case "book", "monograph", "edited-book", "reference-book", "textbook" -> ResourceType.BOOK;
            case "posted-content" -> ResourceType.PREPRINT;
            case "dissertation" -> ResourceType.THESIS;
            case "dataset" -> ResourceType.DATASET;
            default -> ResourceType.OTHER;
        };
    }

    private static String first(JsonNode arr) {
        return arr != null && arr.isArray() && arr.size() > 0 && arr.get(0).isTextual() ? arr.get(0).asText() : null;
    }
}
