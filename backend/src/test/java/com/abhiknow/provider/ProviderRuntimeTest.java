package com.abhiknow.provider;

import com.abhiknow.domain.ProviderRecord;
import com.abhiknow.provider.ProviderOutcome.Status;
import com.abhiknow.search.ParsedQuery;
import com.abhiknow.search.QueryParser;
import com.abhiknow.support.TestProps;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.abhiknow.support.Fixtures.record;
import static org.assertj.core.api.Assertions.assertThat;

class ProviderRuntimeTest {
    private final QueryParser parser = new QueryParser();

    interface Behaviour { List<ProviderRecord> run(int call) throws ProviderException; }

    static final class Fake implements SearchProvider {
        final AtomicInteger calls = new AtomicInteger();
        final Behaviour behaviour;
        final long minInterval;
        Fake(Behaviour b) { this(b, 0); }
        Fake(Behaviour b, long minInterval) { this.behaviour = b; this.minInterval = minInterval; }
        public String id() { return "fake"; }
        public String displayName() { return "Fake"; }
        public boolean supports(ParsedQuery q) { return true; }
        public long minIntervalMs() { return minInterval; }
        public List<ProviderRecord> search(ParsedQuery q, int page, int size, Duration t) throws ProviderException {
            return behaviour.run(calls.incrementAndGet());
        }
    }

    private static List<ProviderRecord> one() {
        return List.of(record("fake", "1", 1, "T", com.abhiknow.domain.ResourceType.PAPER, 2020, List.of("A B"), List.of()));
    }

    @Test void successThenCacheHit() {
        ProviderRuntime rt = new ProviderRuntime(TestProps.of(2000, 2, 10));
        Fake f = new Fake(c -> one());
        ParsedQuery q = parser.parse("graph theory");
        assertThat(rt.execute(f, q, 1, 15).outcome().status()).isEqualTo(Status.OK);
        ProviderRuntime.Result second = rt.execute(f, q, 1, 15);
        assertThat(second.outcome().cached()).isTrue();
        assertThat(second.records()).hasSize(1);
        assertThat(f.calls.get()).isEqualTo(1);
    }

    @Test void retriesTransientFailuresWithBackoff() {
        ProviderRuntime rt = new ProviderRuntime(TestProps.of(3000, 2, 10));
        Fake f = new Fake(c -> {
            if (c < 3) throw new ProviderException(ProviderException.Kind.UNAVAILABLE, "boom");
            return one();
        });
        ProviderRuntime.Result r = rt.execute(f, parser.parse("retry me"), 1, 15);
        assertThat(r.outcome().status()).isEqualTo(Status.OK);
        assertThat(f.calls.get()).isEqualTo(3);
    }

    @Test void rateLimitedIsNotRetried() {
        ProviderRuntime rt = new ProviderRuntime(TestProps.of(3000, 2, 10));
        Fake f = new Fake(c -> { throw new ProviderException(ProviderException.Kind.RATE_LIMITED, "429"); });
        ProviderRuntime.Result r = rt.execute(f, parser.parse("limited"), 1, 15);
        assertThat(r.outcome().status()).isEqualTo(Status.RATE_LIMITED);
        assertThat(r.records()).isEmpty();
        assertThat(f.calls.get()).isEqualTo(1);
    }

    @Test void timeoutBecomesOutcomeNotException() {
        ProviderRuntime rt = new ProviderRuntime(TestProps.of(2000, 0, 10));
        Fake f = new Fake(c -> { throw new ProviderException(ProviderException.Kind.TIMEOUT, "slow"); });
        assertThat(rt.execute(f, parser.parse("slow"), 1, 15).outcome().status()).isEqualTo(Status.TIMEOUT);
    }

    @Test void unexpectedRuntimeExceptionIsIsolated() {
        ProviderRuntime rt = new ProviderRuntime(TestProps.of(2000, 2, 10));
        Fake f = new Fake(c -> { throw new NullPointerException("bad data"); });
        ProviderRuntime.Result r = rt.execute(f, parser.parse("npe"), 1, 15);
        assertThat(r.outcome().status()).isEqualTo(Status.ERROR);
        assertThat(f.calls.get()).isEqualTo(1);
    }

    @Test void circuitOpensAfterRepeatedFailuresAndRecoversAfterWindow() {
        MutableClock clock = new MutableClock();
        ProviderRuntime rt = new ProviderRuntime(TestProps.of(2000, 0, 10), clock);
        boolean[] healthy = {false};
        Fake f = new Fake(c -> { if (!healthy[0]) throw new ProviderException(ProviderException.Kind.HTTP_ERROR, "500"); return one(); });
        for (int i = 0; i < 3; i++) rt.execute(f, parser.parse("q" + i), 1, 15);
        assertThat(rt.isCircuitOpen("fake")).isTrue();
        int before = f.calls.get();
        assertThat(rt.execute(f, parser.parse("another"), 1, 15).outcome().status()).isEqualTo(Status.CIRCUIT_OPEN);
        assertThat(f.calls.get()).isEqualTo(before);

        clock.advance(Duration.ofSeconds(31));
        healthy[0] = true;
        assertThat(rt.execute(f, parser.parse("after window"), 1, 15).outcome().status()).isEqualTo(Status.OK);
        assertThat(rt.isCircuitOpen("fake")).isFalse();
    }

    @Test void requestSpacingBeyondDeadlineIsReportedNotSlept() {
        ProviderRuntime rt = new ProviderRuntime(TestProps.of(300, 0, 10));
        Fake f = new Fake(c -> one(), 5000);
        assertThat(rt.execute(f, parser.parse("first"), 1, 15).outcome().status()).isEqualTo(Status.OK);
        long t = System.nanoTime();
        ProviderRuntime.Result second = rt.execute(f, parser.parse("second"), 1, 15);
        assertThat(second.outcome().status()).isEqualTo(Status.RATE_LIMITED);
        assertThat((System.nanoTime() - t) / 1_000_000).isLessThan(250);
    }

    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        public ZoneId getZone() { return ZoneId.of("UTC"); }
        public Clock withZone(ZoneId z) { return this; }
        public Instant instant() { return now; }
    }
}
