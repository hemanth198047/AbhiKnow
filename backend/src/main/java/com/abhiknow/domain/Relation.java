package com.abhiknow.domain;

/**
 * Relationship to another resource that is related but deliberately not merged.
 * Before persistence targetKey is an in-memory key; afterwards it is the target's UUID string.
 */
public record Relation(String kind, String targetKey, String targetTitle) {
    public static final String PREPRINT_OF = "PREPRINT_OF";   // this resource is a preprint of the target
    public static final String HAS_PREPRINT = "HAS_PREPRINT"; // inverse, derived when reading
    public static final String EDITION = "EDITION";           // another edition of the same work
}
