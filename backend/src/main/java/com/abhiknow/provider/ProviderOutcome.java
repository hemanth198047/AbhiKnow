package com.abhiknow.provider;

/** Per-provider result of one search, returned to the client so coverage is never overstated. */
public record ProviderOutcome(String providerId, String displayName, Status status, int recordCount,
                              long latencyMs, String message, boolean cached) {
    public enum Status { OK, TIMEOUT, ERROR, RATE_LIMITED, SKIPPED, CIRCUIT_OPEN, DISABLED }
}
