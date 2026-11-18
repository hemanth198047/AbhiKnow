package com.abhiknow.search;

import com.abhiknow.domain.ResourceType;

import java.util.Set;

public record SearchRequest(String q, int page, Set<ResourceType> types, Integer yearFrom, Integer yearTo,
                            String language, AccessFilter access, Set<String> providers) {
    /** ANY: no filter. FREE: provider-asserted free to read. OPEN: public domain / open license only. */
    public enum AccessFilter { ANY, FREE, OPEN }
}
