package com.abhiknow.domain;

import java.util.List;

/** What one provider said about one work. Immutable; carries provenance (providerId + providerRef). */
public record ProviderRecord(
        String providerId,
        String providerRef,
        int rank,                 // 1-based position in the provider's own result list
        String title,
        String subtitle,
        String description,
        List<String> authors,
        String publisher,
        Integer year,
        String language,
        ResourceType type,
        List<Identifier> identifiers,
        List<String> relatedDois, // e.g. the journal DOI an arXiv preprint points to; a relation, not identity
        List<Location> locations,
        List<String> subjects,
        Integer citedBy) {
}
