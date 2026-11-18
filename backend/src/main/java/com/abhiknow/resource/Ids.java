package com.abhiknow.resource;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Identifier parsing and normalization: DOI, ISBN, arXiv id, URL. */
public final class Ids {
    private static final Pattern DOI = Pattern.compile("(10\\.\\d{4,9}/[^\\s\"<>]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARXIV_NEW = Pattern.compile("^(?:arxiv:)?(\\d{4}\\.\\d{4,5})(?:v\\d+)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARXIV_OLD = Pattern.compile("^(?:arxiv:)?([a-z\\-]+(?:\\.[A-Z]{2})?/\\d{7})(?:v\\d+)?$", Pattern.CASE_INSENSITIVE);

    private Ids() {}

    /** Returns lowercase bare DOI (no URL prefix) or null. */
    public static String doi(String s) {
        if (s == null) return null;
        Matcher m = DOI.matcher(s.trim());
        if (!m.find()) return null;
        String d = m.group(1).toLowerCase(Locale.ROOT);
        while (d.endsWith(".") || d.endsWith(",") || d.endsWith(")") || d.endsWith(";")) d = d.substring(0, d.length() - 1);
        return d;
    }

    /** Returns a valid ISBN-13 (digits only) or null. Converts ISBN-10 and verifies check digits. */
    public static String isbn13(String s) {
        if (s == null) return null;
        String d = s.replaceAll("[\\s\\-]", "").toUpperCase(Locale.ROOT);
        if (d.matches("\\d{13}")) return validIsbn13(d) ? d : null;
        if (d.matches("\\d{9}[\\dX]")) {
            if (!validIsbn10(d)) return null;
            String core = "978" + d.substring(0, 9);
            return core + isbn13Check(core);
        }
        return null;
    }

    private static boolean validIsbn10(String d) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = d.charAt(i);
            int v = c == 'X' ? 10 : c - '0';
            sum += v * (10 - i);
        }
        return sum % 11 == 0;
    }

    private static boolean validIsbn13(String d) {
        return isbn13Check(d.substring(0, 12)) == d.charAt(12);
    }

    private static char isbn13Check(String twelve) {
        int sum = 0;
        for (int i = 0; i < 12; i++) sum += (twelve.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        return (char) ('0' + (10 - sum % 10) % 10);
    }

    /** Bare arXiv id without version (e.g. 2301.00001 or cs/0112017) or null. */
    public static String arxiv(String s) {
        if (s == null) return null;
        String t = s.trim().replaceFirst("(?i)^https?://(?:export\\.)?arxiv\\.org/(?:abs|pdf)/", "").replaceFirst("\\.pdf$", "");
        Matcher n = ARXIV_NEW.matcher(t);
        if (n.matches()) return n.group(1);
        Matcher o = ARXIV_OLD.matcher(t);
        if (o.matches()) return o.group(1).toLowerCase(Locale.ROOT);
        return null;
    }

    /** DOI that DataCite assigns to every arXiv paper. */
    public static String arxivDoi(String arxivId) {
        return "10.48550/arxiv." + arxivId.toLowerCase(Locale.ROOT);
    }

    /** Only http(s) links are ever stored or returned; anything else (javascript:, data:) is dropped. */
    public static String safeUrl(String url) {
        if (url == null) return null;
        String u = url.trim();
        String lower = u.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("https://") || lower.startsWith("http://"))) return null;
        if (u.chars().anyMatch(Character::isISOControl) || u.contains(" ")) return null;
        return u;
    }
}
