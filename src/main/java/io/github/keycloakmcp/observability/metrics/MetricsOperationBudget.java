package io.github.keycloakmcp.observability.metrics;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

import io.github.keycloakmcp.config.MetricsConfig;
import io.github.keycloakmcp.collection.CollectionBudget;

/**
 * Invocation-local cooperative deadline, shared explicitly by nested metrics calls.
 * Uses elapsed monotonic time, not wall-clock timestamps; never stores identity or credentials.
 */
public final class MetricsOperationBudget {
    public static final String REASON_EXCEEDED = "OPERATION_BUDGET_EXCEEDED";
    public static final String REASON_INTERRUPTED = "OPERATION_INTERRUPTED";
    public static final Duration MAX_DURATION = Duration.ofMinutes(2);

    private final LongSupplier nanoClock;
    private final long startedAt;
    private final long durationNanos;
    private final CollectionBudget parent;

    public static MetricsOperationBudget start(Duration duration) {
        return new MetricsOperationBudget(duration, System::nanoTime);
    }

    public static MetricsOperationBudget fromConfig(MetricsConfig config) {
        Objects.requireNonNull(config, "Metrics configuration is required");
        return start(Duration.ofMillis(config.operationTimeoutMs()));
    }

    /** Clock injection for deterministic deadline tests; production uses {@link #start(Duration)}. */
    public MetricsOperationBudget(Duration duration, LongSupplier nanoClock) {
        if (duration == null || duration.isZero() || duration.isNegative() || duration.compareTo(MAX_DURATION) > 0) {
            throw new IllegalArgumentException("Metrics operation duration must be positive and at most 120000 ms");
        }
        this.nanoClock = Objects.requireNonNull(nanoClock, "Monotonic clock is required");
        this.durationNanos = duration.toNanos();
        this.startedAt = nanoClock.getAsLong();
        // Capture on the caller thread. HTTP callbacks must never consult thread-local scope.
        this.parent = CollectionBudget.current();
    }

    public Duration remaining() {
        if (Thread.currentThread().isInterrupted()) return Duration.ZERO;
        // Subtraction remains correct across nanoTime wrap for this bounded interval.
        long elapsed = nanoClock.getAsLong() - startedAt;
        if (elapsed < 0 || elapsed >= durationNanos) return Duration.ZERO;
        Duration local = Duration.ofNanos(durationNanos - elapsed);
        Duration inherited = parent == null ? local : parent.remaining();
        return inherited.compareTo(local) < 0 ? inherited : local;
    }

    public boolean exhausted() {
        return remaining().isZero();
    }

    public String reason() {
        if (Thread.currentThread().isInterrupted()) return REASON_INTERRUPTED;
        return parent != null && parent.exhausted() ? parent.reason() : REASON_EXCEEDED;
    }

    /** Never rounds a short remaining deadline up to a minimum transport timeout. */
    public Duration cap(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Positive transport timeout is required");
        }
        Duration remaining = remaining();
        return timeout.compareTo(remaining) < 0 ? timeout : remaining;
    }
}
