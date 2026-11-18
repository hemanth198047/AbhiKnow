package com.abhiknow.search;

import java.util.List;

/**
 * Query after deterministic parsing. {@code text} is what keyword providers receive;
 * {@code raw} is what the user typed. {@code mode} decides which providers can answer.
 */
public record ParsedQuery(String raw, String text, Mode mode, List<String> phrases,
                          String doi, String isbn, String arxivId, String author) {
    public enum Mode { KEYWORD, DOI, ISBN, ARXIV, AUTHOR }

    public String cacheKey() {
        return mode + "|" + text + "|" + doi + "|" + isbn + "|" + arxivId + "|" + author;
    }
}
