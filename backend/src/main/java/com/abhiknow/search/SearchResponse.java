package com.abhiknow.search;

import com.abhiknow.provider.ProviderOutcome;

import java.util.List;
import java.util.Map;

public record SearchResponse(QueryInfo query, int page, List<ResourceDto> results, List<String> recommendedIds, Map<String, Integer> countsByType,
                             int totalBeforeFilters, List<ProviderOutcome> providers, long durationMs,
                             String coverage, List<String> warnings) {

    public record QueryInfo(String raw, String searchedAs, String mode) {}
}
