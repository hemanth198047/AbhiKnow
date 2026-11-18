package com.abhiknow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** All runtime configuration. Secrets (API keys) come from the environment, never from source. */
@ConfigurationProperties(prefix = "abhiknow")
public record AbhiKnowProperties(
        @DefaultValue("you@example.com") String contactEmail,
        @DefaultValue("8000") int providerTimeoutMs,
        @DefaultValue("15") int pageSize,
        @DefaultValue("8") int maxConcurrentProviderCalls,
        @DefaultValue("2") int maxRetries,
        @DefaultValue("300") int backoffBaseMs,
        @DefaultValue("600000") long cacheTtlMs,
        String openalexApiKey,
        @DefaultValue("http://localhost:5173") String corsOrigins,
        @DefaultValue Urls urls) {

    public record Urls(
            @DefaultValue("https://api.openalex.org") String openalex,
            @DefaultValue("https://api.crossref.org") String crossref,
            @DefaultValue("https://export.arxiv.org") String arxiv,
            @DefaultValue("https://openlibrary.org") String openlibrary,
            @DefaultValue("https://gutendex.com") String gutendex) {
    }

    public String userAgent() {
        return "AbhiKnow/0.1 (mailto:" + contactEmail + ")";
    }
}
