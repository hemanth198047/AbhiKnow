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
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Duration;
import java.util.*;

import static com.abhiknow.provider.ProviderSupport.enc;
import static com.abhiknow.provider.ProviderSupport.location;

/**
 * arXiv Atom API. Published limit: one request per 3 seconds (enforced via minIntervalMs).
 * The attribution line required by arXiv is shown in the UI footer.
 */
@Component
public class ArxivProvider implements SearchProvider {
    private static final String ATOM = "http://www.w3.org/2005/Atom";
    private static final String ARXIV_NS = "http://arxiv.org/schemas/atom";
    private final HttpFetcher http;
    private final AbhiKnowProperties props;

    public ArxivProvider(HttpFetcher http, AbhiKnowProperties props) {
        this.http = http;
        this.props = props;
    }

    @Override public String id() { return "arxiv"; }
    @Override public String displayName() { return "arXiv"; }
    @Override public long minIntervalMs() { return 3000; }

    @Override
    public boolean supports(ParsedQuery q) {
        return q.mode() == ParsedQuery.Mode.KEYWORD || q.mode() == ParsedQuery.Mode.ARXIV || q.mode() == ParsedQuery.Mode.AUTHOR;
    }

    @Override
    public List<ProviderRecord> search(ParsedQuery q, int page, int pageSize, Duration timeout) throws ProviderException {
        String base = props.urls().arxiv() + "/api/query?";
        String url;
        if (q.mode() == ParsedQuery.Mode.ARXIV) {
            url = base + "id_list=" + enc(q.arxivId()) + "&max_results=1";
        } else {
            url = base + "search_query=" + enc(buildQuery(q)) + "&start=" + (page - 1) * pageSize
                    + "&max_results=" + pageSize + "&sortBy=relevance&sortOrder=descending";
        }
        return parse(http.get(url, timeout), (page - 1) * pageSize);
    }

    /** Translate to arXiv syntax: all:term AND all:term, quoted phrases kept intact, au:"name" for authors. */
    static String buildQuery(ParsedQuery q) {
        List<String> parts = new ArrayList<>();
        if (q.mode() == ParsedQuery.Mode.AUTHOR) parts.add("au:\"" + q.author().replace("\"", "") + "\"");
        String rest = q.mode() == ParsedQuery.Mode.AUTHOR && q.text().equals(q.author()) ? "" : q.text();
        for (String ph : q.phrases()) {
            parts.add("all:\"" + ph.replace("\"", "") + "\"");
            rest = rest.replace("\"" + ph + "\"", " ");
        }
        int n = 0;
        for (String t : Text.normalize(rest).split(" ")) {
            if (t.isEmpty() || t.length() < 2) continue;
            if (n++ >= 8) break;
            parts.add("all:" + t);
        }
        if (parts.isEmpty()) parts.add("all:" + Text.normalize(q.raw()));
        return String.join(" AND ", parts);
    }

    static List<ProviderRecord> parse(String xml, int rankOffset) throws ProviderException {
        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            // XXE hardening: provider XML is untrusted.
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new ProviderException(ProviderException.Kind.PARSE_ERROR, "Provider returned malformed XML", e);
        }
        NodeList entries = doc.getElementsByTagNameNS(ATOM, "entry");
        List<ProviderRecord> out = new ArrayList<>();
        int rank = rankOffset;
        for (int i = 0; i < entries.getLength(); i++) {
            rank++;
            ProviderRecord r = mapEntry((Element) entries.item(i), rank);
            if (r != null) out.add(r);
        }
        return out;
    }

    private static ProviderRecord mapEntry(Element e, int rank) {
        String arxivId = Ids.arxiv(child(e, ATOM, "id"));
        String title = Text.collapse(child(e, ATOM, "title"));
        if (arxivId == null || title == null || title.equalsIgnoreCase("Error")) return null;

        List<String> authors = new ArrayList<>();
        NodeList an = e.getElementsByTagNameNS(ATOM, "author");
        for (int i = 0; i < an.getLength() && authors.size() < 25; i++) {
            String n = Text.collapse(child((Element) an.item(i), ATOM, "name"));
            if (n != null) authors.add(n);
        }

        AccessEvidence ev = AccessClassifier.arxiv();
        List<Location> locs = new ArrayList<>();
        String pdf = null;
        NodeList links = e.getElementsByTagNameNS(ATOM, "link");
        for (int i = 0; i < links.getLength(); i++) {
            Element l = (Element) links.item(i);
            if ("pdf".equals(l.getAttribute("title"))) pdf = l.getAttribute("href");
        }
        Location abs = location("arxiv", "https://arxiv.org/abs/" + arxivId, "LANDING", "html", ev, null);
        if (abs != null) locs.add(abs);
        Location pdfLoc = location("arxiv", pdf != null ? pdf : "https://arxiv.org/pdf/" + arxivId, "PDF", "pdf", ev, null);
        if (pdfLoc != null) locs.add(pdfLoc);

        List<String> subjects = new ArrayList<>();
        NodeList cats = e.getElementsByTagNameNS(ATOM, "category");
        for (int i = 0; i < cats.getLength() && subjects.size() < 6; i++) {
            String t = ((Element) cats.item(i)).getAttribute("term");
            if (!t.isBlank()) subjects.add(t);
        }

        List<String> related = new ArrayList<>();
        String journalDoi = Ids.doi(child(e, ARXIV_NS, "doi"));
        if (journalDoi != null) related.add(journalDoi);

        List<Identifier> ids = List.of(new Identifier(IdScheme.ARXIV, arxivId),
                new Identifier(IdScheme.DOI, Ids.arxivDoi(arxivId)));
        Integer year = null;
        String published = child(e, ATOM, "published");
        if (published != null && published.length() >= 4 && published.substring(0, 4).matches("\\d{4}")) {
            year = Integer.parseInt(published.substring(0, 4));
        }
        return new ProviderRecord("arxiv", arxivId, rank, title, null, Text.truncate(Text.collapse(child(e, ATOM, "summary")), 1500),
                authors, "arXiv", year, "en", ResourceType.PREPRINT, ids, related, locs, subjects, null);
    }

    private static String child(Element parent, String ns, String local) {
        NodeList nl = parent.getElementsByTagNameNS(ns, local);
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getParentNode() == parent) return n.getTextContent();
        }
        return null;
    }
}
