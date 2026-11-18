package com.abhiknow.support;

import com.abhiknow.config.AbhiKnowProperties;

public final class TestProps {
    private TestProps() {}

    public static AbhiKnowProperties of(int timeoutMs, int maxRetries, int backoffMs, AbhiKnowProperties.Urls urls) {
        return new AbhiKnowProperties("tester@example.org", timeoutMs, 15, 8, maxRetries, backoffMs, 60_000, null,
                "http://localhost:5173", urls);
    }

    public static AbhiKnowProperties of(int timeoutMs, int maxRetries, int backoffMs) {
        return of(timeoutMs, maxRetries, backoffMs, new AbhiKnowProperties.Urls(
                "http://localhost:1", "http://localhost:1", "http://localhost:1", "http://localhost:1", "http://localhost:1"));
    }
}
