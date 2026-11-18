package com.abhiknow.search;

import com.abhiknow.access.AccessClassifier;
import com.abhiknow.domain.*;

import java.util.List;

/** API representation of a resource, with presentation fields derived from access evidence. */
public record ResourceDto(String id, String title, String subtitle, String description, String type, String language,
                          String publisher, Integer year, List<String> authors, List<IdentifierDto> identifiers,
                          List<LocationDto> locations, List<String> sources, List<String> subjects, Integer citedBy,
                          String accessType, String accessLabel, String recommendedAction, double score,
                          List<String> reasons, List<RelationDto> relations) {

    public record IdentifierDto(String scheme, String value) {}

    public record LocationDto(String provider, String url, String kind, String format, String accessType, String accessLabel,
                              String license, String canRead, String canDownload, String canRedistribute, String note) {}

    public record RelationDto(String kind, String id, String title) {}

    public static ResourceDto from(Resource r) {
        return new ResourceDto(r.id() == null ? null : r.id().toString(), r.title(), r.subtitle(), r.description(),
                r.type().name(), r.language(), r.publisher(), r.year(), r.authors(),
                r.identifiers().stream().map(i -> new IdentifierDto(i.scheme().name(), i.value())).toList(),
                r.locations().stream().map(l -> new LocationDto(l.providerId(), l.url(), l.kind(), l.format(), l.accessType().name(),
                        l.accessType().label(), l.license(), l.canRead().name(), l.canDownload().name(),
                        l.canRedistribute().name(), l.note())).toList(),
                r.sources().stream().map(SourceRef::providerId).distinct().toList(), r.subjects(), r.citedBy(),
                r.accessType().name(), r.accessType().label(), AccessClassifier.recommendedAction(r.accessType()),
                r.score(), r.reasons(),
                r.relations().stream().map(x -> new RelationDto(x.kind(), x.targetKey(), x.targetTitle())).toList());
    }
}
