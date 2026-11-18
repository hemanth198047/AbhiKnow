package com.abhiknow.resource;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Text normalization shared by dedup, ranking and adapters. */
public final class Text {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern WS = Pattern.compile("\\s+");
    private static final Set<String> STOP = Set.of("a", "an", "the", "of", "and", "or", "in", "on", "to", "for", "with", "is", "are", "from");

    private Text() {}

    /** Lowercase, strip diacritics and punctuation, collapse whitespace. */
    public static String normalize(String s) {
        if (s == null) return "";
        String d = DIACRITICS.matcher(Normalizer.normalize(s, Normalizer.Form.NFKD)).replaceAll("");
        return WS.matcher(NON_ALNUM.matcher(d.toLowerCase(Locale.ROOT)).replaceAll(" ")).replaceAll(" ").trim();
    }

    public static List<String> terms(String s) {
        String n = normalize(s);
        if (n.isEmpty()) return List.of();
        return Arrays.stream(n.split(" ")).filter(t -> !STOP.contains(t)).distinct().collect(Collectors.toList());
    }

    /** Remove markup (e.g. JATS abstracts) and decode the few entities we expect. Output is plain text. */
    public static String stripMarkup(String s) {
        if (s == null) return null;
        String t = TAGS.matcher(s).replaceAll(" ")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
        t = WS.matcher(t).replaceAll(" ").trim();
        return t.isEmpty() ? null : t;
    }

    public static String collapse(String s) {
        if (s == null) return null;
        String t = WS.matcher(s).replaceAll(" ").trim();
        return t.isEmpty() ? null : t;
    }

    public static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max - 1).trim() + "…";
    }

    /** Similarity in [0,1] from Levenshtein distance on normalized strings. */
    public static double similarity(String a, String b) {
        String x = normalize(a), y = normalize(b);
        if (x.isEmpty() || y.isEmpty()) return 0;
        if (x.equals(y)) return 1;
        int max = Math.max(x.length(), y.length());
        return 1.0 - (double) levenshtein(x, y) / max;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    /** Last name of "First Last" or "Last, First". */
    public static String lastName(String author) {
        if (author == null || author.isBlank()) return "";
        String a = author.trim();
        if (a.contains(",")) return normalize(a.substring(0, a.indexOf(',')));
        String[] parts = a.split("\\s+");
        return normalize(parts[parts.length - 1]);
    }

    /** "Last, First" -> "First Last"; other forms unchanged. */
    public static String displayName(String author) {
        if (author == null) return null;
        String a = collapse(author);
        if (a == null) return null;
        int c = a.indexOf(',');
        if (c > 0 && a.indexOf(',', c + 1) < 0) {
            String last = a.substring(0, c).trim(), first = a.substring(c + 1).trim();
            return first.isEmpty() ? last : first + " " + last;
        }
        return a;
    }
}
