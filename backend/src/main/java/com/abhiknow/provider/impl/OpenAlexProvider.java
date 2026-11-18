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

/** OpenAlex works search. Needs a free API key in practice (see PROVIDER_REGISTRY); key comes from configuration. */
@Component
public class OpenAlexProvider implements SearchProvider {
    static final String SELECT = "id,doi,title,display_name,publication_year,type,language,authorships,"
            + "primary_location,open_access,best_oa_location,cited_by_count,abstract_inverted_index";
    private final HttpFetcher http;
    private final AbhiKnowProperties props;

    public OpenAlexProvider(HttpFetcher http, AbhiKnowProperties props) {
        this.http = http;
        this.props = props;
    }

    @Override public String id() { return "openalex"; }
    @Override public String displayName() { return "OpenAlex"; }

    @Override
    public boolean supports(ParsedQuery q) {
        return q.mode() == ParsedQuery.Mode.KEYWORD || q.mode() == ParsedQuery.Mode.DOI;
    }

    @Override
    public List<ProviderRecord> search(ParsedQuery q, int page, int pageSize, Duration timeout) throws ProviderException {
        StringBuilder url = new StringBuilder(props.urls().openalex()).append("/works?");
        if (q.mode() == ParsedQuery.Mode.DOI) {
            url.append("filter=doi:").append(enc("https://doi.org/" + q.doi()));
        } else {
            url.append("search=").append(enc(q.text()));
        }
        url.append("&per-page=").append(pageSize).append("&page=").append(page)
                .append("&select=").append(SELECT)
                .append("&mailto=").append(enc(props.contactEmail()));
        if (props.openalexApiKey() != null && !props.openalexApiKey().isBlank()) {
            url.append("&api_key=").append(enc(props.openalexApiKey()));
        }
        JsonNode root = parseJson(http.get(url.toString(), timeout));
        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            throw new ProviderException(ProviderException.Kind.PARSE_ERROR, "Unexpected OpenAlex response shape");
        }
        List<ProviderRecord> out = new ArrayList<>();
        int rank = (page - 1) * pageSize;
        for (JsonNode w : results) {
            rank++;
            ProviderRecord r = map(w, rank);
            if (r != null) out.add(r);
        }
        return out;
    }

    static ProviderRecord map(JsonNode w, int rank) {
        String title = Text.collapse(firstNonNull(text(w, "title"), text(w, "display_name")));
        String idUrl = text(w, "id");
        if (title == null || idUrl == null) return null;
        String ref = idUrl.substring(idUrl.lastIndexOf('/') + 1);

        List<Identifier> ids = new ArrayList<>();
        ids.add(new Identifier(IdScheme.OPENALEX, ref));
        String doi = Ids.doi(text(w, "doi"));
        if (doi != null) ids.add(new Identifier(IdScheme.DOI, doi));

        List<String> authors = new ArrayList<>();
        for (JsonNode a : w.path("authorships")) {
            String n = text(a.path("author"), "display_name");
            if (n != null && authors.size() < 25) authors.add(n);
        }

        JsonNode oa = w.path("open_access");
        JsonNode best = w.path("best_oa_location");
        boolean isOa = oa.path("is_oa").asBoolean(false);
        String license = best.isMissingNode() || best.isNull() ? null : text(best, "license");
        AccessEvidence ev = AccessClassifier.openAlex(isOa, text(oa, "oa_status"), license);

        List<Location> locs = new ArrayList<>();
        if (isOa && best.isObject()) {
            addLoc(locs, location("openalex", text(best, "pdf_url"), "PDF", "pdf", ev, license));
            addLoc(locs, location("openalex", text(best, "landing_page_url"), "LANDING", "html", ev, license));
        }
        if (locs.isEmpty()) {
            String landing = text(w.path("primary_location"), "landing_page_url");
            addLoc(locs, location("openalex", landing != null ? landing : (doi != null ? "https://doi.org/" + doi : null),
                    "LANDING", "html", isOa ? ev : AccessClassifier.openAlex(false, null, null), null));
        }

        String publisher = text(w.path("primary_location").path("source"), "display_name");
        return new ProviderRecord("openalex", ref, rank, title, null, abstractOf(w.path("abstract_inverted_index")),
                authors, publisher, intVal(w, "publication_year"), Languages.normalize(text(w, "language")),
                type(text(w, "type")), ids, List.of(), locs, List.of(), intVal(w, "cited_by_count"));
    }

    static ResourceType type(String t) {
        if (t == null) return ResourceType.OTHER;
        return switch (t) {
            case "article", "review", "letter", "editorial", "paratext" -> ResourceType.PAPER;
            case "preprint" -> ResourceType.PREPRINT;
            case "book" -> ResourceType.BOOK;
            case "dissertation" -> ResourceType.THESIS;
            case "dataset" -> ResourceType.DATASET;
            default -> ResourceType.OTHER;
        };
    }

    /** OpenAlex ships abstracts as an inverted index (word -> positions). Rebuild plain text. */
    static String abstractOf(JsonNode inv) {
        if (inv == null || !inv.isObject() || inv.isEmpty()) return null;
        TreeMap<Integer, String> byPos = new TreeMap<>();
        inv.fields().forEachRemaining(e -> e.getValue().forEach(p -> byPos.put(p.asInt(), e.getKey())));
        String s = String.join(" ", byPos.values());
        return Text.truncate(Text.collapse(s), 1500);
    }

    private static void addLoc(List<Location> l, Location x) { if (x != null) l.add(x); }

    private static String firstNonNull(String a, String b) { return a != null ? a : b; }
}
