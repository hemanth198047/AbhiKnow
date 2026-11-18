package com.abhiknow.search;

import com.abhiknow.resource.Ids;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic query understanding (no LLM). Detects DOI, ISBN, arXiv id, author:"..." and quoted phrases,
 * and removes conversational filler so providers receive keywords. The stripped text is shown to the user.
 */
@Component
public class QueryParser {
    private static final Pattern AUTHOR = Pattern.compile("(?i)\\bauthor:\\s*(?:\"([^\"]+)\"|(\\S+(?:\\s+\\S+)?))");
    private static final Pattern PHRASE = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern LEADING = Pattern.compile(
            "(?i)^\\s*(?:i\\s+(?:want|would like|need)\\s+to\\s+(?:become\\s+(?:an?\\s+)?(?:expert|master|professional)\\s+in|learn(?:\\s+about)?|understand|study|research|master)"
                    + "|(?:teach|show|help)\\s+me(?:\\s+(?:about|to learn))?|how\\s+(?:do|can)\\s+i\\s+learn|learn(?:ing)?(?:\\s+about)?|resources\\s+(?:on|about|for)|books\\s+(?:on|about))\\s+");
    private static final Pattern TRAILING = Pattern.compile(
            "(?i)\\s*(?:from\\s+(?:a\\s+)?beginner\\s+to\\s+(?:advanced|expert)(?:\\s+(?:level|research))?|for\\s+beginners|beginner\\s+to\\s+advanced(?:\\s+research)?)\\s*[.?!]*\\s*$");

    public ParsedQuery parse(String raw) {
        String q = raw == null ? "" : raw.trim();
        if (q.isEmpty()) throw new IllegalArgumentException("Query must not be empty");

        String doi = Ids.doi(q);
        if (doi != null && q.replaceAll("(?i)^(doi:|https?://(dx\\.)?doi\\.org/)", "").trim().toLowerCase().startsWith("10.")) {
            return new ParsedQuery(q, doi, ParsedQuery.Mode.DOI, List.of(), doi, null, null, null);
        }
        String isbn = Ids.isbn13(q.replaceFirst("(?i)^isbn[:\\s]*", ""));
        if (isbn != null) {
            return new ParsedQuery(q, isbn, ParsedQuery.Mode.ISBN, List.of(), null, isbn, null, null);
        }
        String arxiv = Ids.arxiv(q);
        if (arxiv != null) {
            return new ParsedQuery(q, arxiv, ParsedQuery.Mode.ARXIV, List.of(), null, null, arxiv, null);
        }

        Matcher am = AUTHOR.matcher(q);
        if (am.find()) {
            String author = am.group(1) != null ? am.group(1) : am.group(2);
            String rest = (q.substring(0, am.start()) + " " + q.substring(am.end())).trim();
            return new ParsedQuery(q, rest.isEmpty() ? author : rest, ParsedQuery.Mode.AUTHOR,
                    phrases(rest), null, null, null, author.trim());
        }

        List<String> phrases = phrases(q);
        String text = q;
        if (phrases.isEmpty()) {
            text = LEADING.matcher(text).replaceFirst("");
            text = TRAILING.matcher(text).replaceFirst("");
            text = text.replaceAll("[.?!]+$", "").trim();
            if (text.isEmpty()) text = q;
        }
        return new ParsedQuery(q, text, ParsedQuery.Mode.KEYWORD, phrases, null, null, null, null);
    }

    private static List<String> phrases(String s) {
        List<String> out = new ArrayList<>();
        Matcher m = PHRASE.matcher(s);
        while (m.find()) if (!m.group(1).isBlank()) out.add(m.group(1).trim());
        return out;
    }
}
