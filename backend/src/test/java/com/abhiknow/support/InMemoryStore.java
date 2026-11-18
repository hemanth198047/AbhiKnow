package com.abhiknow.support;

import com.abhiknow.domain.Resource;
import com.abhiknow.provider.ProviderOutcome;
import com.abhiknow.search.ParsedQuery;
import com.abhiknow.store.ResourceStore;

import java.util.*;

public class InMemoryStore implements ResourceStore {
    public final Map<UUID, Resource> data = new LinkedHashMap<>();
    public final List<ProviderInfo> registry = new ArrayList<>();
    public int searches;

    public InMemoryStore trust(String id, String status) {
        registry.add(new ProviderInfo(id, id, "https://example.org", status, null, true, null, null));
        return this;
    }

    @Override public List<Resource> saveAll(List<Resource> resources) {
        List<Resource> out = new ArrayList<>();
        for (Resource r : resources) {
            Resource saved = r.withId(UUID.nameUUIDFromBytes(r.key().getBytes()));
            data.put(saved.id(), saved);
            out.add(saved);
        }
        return out;
    }
    @Override public Optional<Resource> find(UUID id) { return Optional.ofNullable(data.get(id)); }
    @Override public void recordSearch(ParsedQuery q, Map<String, Object> f, List<ProviderOutcome> o, int n, long ms) { searches++; }
    @Override public void recordUsage(ProviderOutcome o) {}
    @Override public List<ProviderInfo> providers() { return registry; }
}
