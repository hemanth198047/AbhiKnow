package com.abhiknow.provider;

import com.abhiknow.config.AbhiKnowProperties;
import com.abhiknow.domain.ProviderRecord;
import com.abhiknow.provider.ProviderOutcome.Status;
import com.abhiknow.search.ParsedQuery;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resilience wrapper around every adapter call: cache, circuit breaker, rate-limit spacing,
 * retries with exponential backoff, an overall per-provider deadline, and failure isolation.
 * {@link #execute} never throws; every failure becomes a ProviderOutcome.
 */
@Component
public class ProviderRuntime {
    private static final int FAILURE_THRESHOLD = 3;
    private static final long OPEN_MS = 30_000;

    public record Result(ProviderOutcome outcome, List<ProviderRecord> records) {}

    private final AbhiKnowProperties props;
    private final Clock clock;
    private final Cache<String, List<ProviderRecord>> cache;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    @Autowired
    public ProviderRuntime(AbhiKnowProperties props) {
        this(props, Clock.systemUTC());
    }

    ProviderRuntime(AbhiKnowProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        this.cache = Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofMillis(props.cacheTtlMs())).build();
    }

    private static final class State {
        int consecutiveFailures;
        long openUntil;
        long nextSlotAt;
    }

    public boolean isCircuitOpen(String providerId) {
        State s = states.get(providerId);
        if (s == null) return false;
        synchronized (s) { return s.openUntil > clock.millis(); }
    }

    public Result execute(SearchProvider p, ParsedQuery q, int page, int pageSize) {
        long start = System.nanoTime();
        String key = p.id() + "|" + q.cacheKey() + "|" + page + "|" + pageSize;
        List<ProviderRecord> hit = cache.getIfPresent(key);
        if (hit != null) return ok(p, hit, start, true);

        State s = states.computeIfAbsent(p.id(), k -> new State());
        synchronized (s) {
            if (s.openUntil > clock.millis()) {
                return fail(p, Status.CIRCUIT_OPEN, "Temporarily paused after repeated failures", start);
            }
        }

        long deadline = start + Duration.ofMillis(props.providerTimeoutMs()).toNanos();
        ProviderException last = null;
        for (int attempt = 0; attempt <= props.maxRetries(); attempt++) {
            if (!acquireSlot(s, p.minIntervalMs(), deadline)) {
                return fail(p, Status.RATE_LIMITED, "Waiting for this provider's request spacing would exceed the time limit", start);
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) { last = new ProviderException(ProviderException.Kind.TIMEOUT, "Timed out"); break; }
            try {
                List<ProviderRecord> records = p.search(q, page, pageSize, Duration.ofNanos(remaining));
                success(s);
                cache.put(key, records);
                return ok(p, records, start, false);
            } catch (ProviderException e) {
                last = e;
                if (!e.kind().retryable || attempt == props.maxRetries()) break;
                long backoff = (long) props.backoffBaseMs() << attempt;
                if (System.nanoTime() + backoff * 1_000_000 >= deadline) break;
                try { Thread.sleep(backoff); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            } catch (RuntimeException e) {
                last = new ProviderException(ProviderException.Kind.PARSE_ERROR, "Unexpected provider data: " + e.getClass().getSimpleName(), e);
                break;
            }
        }
        failure(s);
        Status status = switch (last.kind()) {
            case TIMEOUT -> Status.TIMEOUT;
            case RATE_LIMITED -> Status.RATE_LIMITED;
            default -> Status.ERROR;
        };
        return fail(p, status, last.getMessage(), start);
    }

    /** Reserve the next request slot for a provider; false if it would start after the deadline. */
    private boolean acquireSlot(State s, long minIntervalMs, long deadlineNanos) {
        if (minIntervalMs <= 0) return true;
        long waitMs;
        synchronized (s) {
            long now = clock.millis();
            long slot = Math.max(now, s.nextSlotAt);
            waitMs = slot - now;
            if (System.nanoTime() + waitMs * 1_000_000 >= deadlineNanos) return false;
            s.nextSlotAt = slot + minIntervalMs;
        }
        if (waitMs > 0) {
            try { Thread.sleep(waitMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        return true;
    }

    private void success(State s) {
        synchronized (s) { s.consecutiveFailures = 0; s.openUntil = 0; }
    }

    private void failure(State s) {
        synchronized (s) {
            if (++s.consecutiveFailures >= FAILURE_THRESHOLD) {
                s.openUntil = clock.millis() + OPEN_MS;
                s.consecutiveFailures = FAILURE_THRESHOLD - 1; // one probe allowed after the window
            }
        }
    }

    private static Result ok(SearchProvider p, List<ProviderRecord> records, long start, boolean cached) {
        return new Result(new ProviderOutcome(p.id(), p.displayName(), Status.OK, records.size(), ms(start), null, cached), records);
    }

    private static Result fail(SearchProvider p, Status status, String message, long start) {
        return new Result(new ProviderOutcome(p.id(), p.displayName(), status, 0, ms(start), message, false), List.of());
    }

    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
