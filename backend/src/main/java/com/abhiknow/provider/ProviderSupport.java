package com.abhiknow.provider;

import com.abhiknow.access.AccessEvidence;
import com.abhiknow.domain.Location;
import com.abhiknow.resource.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Small helpers shared by adapters. */
public final class ProviderSupport {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private ProviderSupport() {}

    public static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static JsonNode parseJson(String body) throws ProviderException {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new ProviderException(ProviderException.Kind.PARSE_ERROR, "Provider returned malformed JSON", e);
        }
    }

    /** Text value or null (never "null" and never blank). */
    public static String text(JsonNode n, String field) {
        if (n == null) return null;
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || !v.isValueNode()) return null;
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    public static Integer intVal(JsonNode n, String field) {
        if (n == null) return null;
        JsonNode v = n.get(field);
        return v != null && v.isNumber() ? v.asInt() : null;
    }

    /** Builds a Location, dropping anything that is not a plain http(s) URL. Returns null when the URL is unusable. */
    public static Location location(String providerId, String url, String kind, String format,
                                    AccessEvidence ev, String license) {
        String safe = Ids.safeUrl(url);
        if (safe == null) return null;
        return new Location(providerId, safe, kind, format, ev.type(), license,
                ev.canRead(), ev.canDownload(), ev.canRedistribute(), ev.note());
    }
}
