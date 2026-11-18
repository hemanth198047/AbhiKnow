package com.abhiknow.domain;

import java.util.List;
import java.util.UUID;

/** Canonical resource after normalization and deduplication. */
public record Resource(
        UUID id,
        String key,                 // stable in-memory key used for relations before persistence
        String title,
        String subtitle,
        String description,
        ResourceType type,
        String language,
        String publisher,
        Integer year,
        List<String> authors,
        List<Identifier> identifiers,
        List<Location> locations,
        List<SourceRef> sources,
        List<String> subjects,
        Integer citedBy,
        AccessType accessType,
        List<Relation> relations,
        double score,
        List<String> reasons) {

    public Resource withId(UUID newId) {
        return new Resource(newId, key, title, subtitle, description, type, language, publisher, year, authors,
                identifiers, locations, sources, subjects, citedBy, accessType, relations, score, reasons);
    }

    public Resource withRanking(double newScore, List<String> newReasons) {
        return new Resource(id, key, title, subtitle, description, type, language, publisher, year, authors,
                identifiers, locations, sources, subjects, citedBy, accessType, relations, newScore, newReasons);
    }

    public Resource withRelations(List<Relation> newRelations) {
        return new Resource(id, key, title, subtitle, description, type, language, publisher, year, authors,
                identifiers, locations, sources, subjects, citedBy, accessType, newRelations, score, reasons);
    }
}
