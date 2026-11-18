package com.abhiknow.store;

import com.abhiknow.domain.Resource;
import com.abhiknow.provider.ProviderOutcome;
import com.abhiknow.search.ParsedQuery;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for discovery. The JDBC implementation is the only one used outside tests. */
public interface ResourceStore {
    /** Upserts resources (matching existing rows by identifier or provider ref) and returns them with ids assigned. */
    List<Resource> saveAll(List<Resource> resources);

    Optional<Resource> find(UUID id);

    void recordSearch(ParsedQuery query, Map<String, Object> filters, List<ProviderOutcome> outcomes, int resultCount, long durationMs);

    void recordUsage(ProviderOutcome outcome);

    List<ProviderInfo> providers();

    record ProviderInfo(String id, String displayName, String baseUrl, String trustStatus, String termsUrl,
                        boolean automationAllowed, String lastReviewed, String notes) {}
}
