package com.abhiknow.resource;

import java.util.Locale;
import java.util.Map;

/** Maps ISO 639-2 codes (Open Library) to ISO 639-1; unknown codes pass through lowercased. */
public final class Languages {
    private static final Map<String, String> MAP = Map.ofEntries(
            Map.entry("eng", "en"), Map.entry("fre", "fr"), Map.entry("fra", "fr"), Map.entry("ger", "de"),
            Map.entry("deu", "de"), Map.entry("spa", "es"), Map.entry("por", "pt"), Map.entry("ita", "it"),
            Map.entry("hin", "hi"), Map.entry("tel", "te"), Map.entry("tam", "ta"), Map.entry("kan", "kn"),
            Map.entry("ben", "bn"), Map.entry("ara", "ar"), Map.entry("chi", "zh"), Map.entry("zho", "zh"),
            Map.entry("jpn", "ja"), Map.entry("rus", "ru"), Map.entry("lat", "la"), Map.entry("dut", "nl"),
            Map.entry("nld", "nl"));

    private Languages() {}

    public static String normalize(String code) {
        if (code == null || code.isBlank()) return null;
        String c = code.trim().toLowerCase(Locale.ROOT);
        if (c.length() == 3) return MAP.getOrDefault(c, c);
        if (c.length() > 2 && c.contains("-")) return c.substring(0, c.indexOf('-'));
        return c;
    }
}
