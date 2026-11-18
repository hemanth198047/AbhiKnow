package com.abhiknow.domain;

/** Provenance: which provider returned this resource, under which provider id, at which rank. */
public record SourceRef(String providerId, String providerRef, int rank) {
}
