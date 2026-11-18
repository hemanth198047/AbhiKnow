package com.abhiknow.support;

import com.abhiknow.domain.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class Fixtures {
    private Fixtures() {}

    public static String read(String name) {
        try (var in = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("Missing fixture " + name, e);
        }
    }

    public static ProviderRecord record(String provider, String ref, int rank, String title, ResourceType type, Integer year,
                                        List<String> authors, List<Identifier> ids) {
        return new ProviderRecord(provider, ref, rank, title, null, null, authors, null, year, null, type, ids, List.of(),
                List.of(), List.of(), null);
    }

    public static Location location(String provider, String url, AccessType t) {
        return new Location(provider, url, "LANDING", "html", t, null, Permission.UNKNOWN, Permission.UNKNOWN, Permission.UNKNOWN, null);
    }
}
