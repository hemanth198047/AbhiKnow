package com.abhiknow.provider;

import com.abhiknow.domain.ProviderRecord;
import com.abhiknow.search.ParsedQuery;

import java.time.Duration;
import java.util.List;

/** Adapter contract. Implementations translate the query, call the provider and normalize into ProviderRecord. */
public interface SearchProvider {
    String id();

    String displayName();

    /** False when the provider cannot meaningfully answer this kind of query; reported as SKIPPED. */
    boolean supports(ParsedQuery query);

    /** Minimum spacing between requests to this provider, to respect published limits. */
    default long minIntervalMs() { return 0; }

    List<ProviderRecord> search(ParsedQuery query, int page, int pageSize, Duration timeout) throws ProviderException;
}
