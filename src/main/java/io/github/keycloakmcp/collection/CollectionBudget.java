package io.github.keycloakmcp.collection;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Synchronous, target-bound collection scope; asynchronous transports capture the budget, not the scope. */
public final class CollectionBudget {
    public static final long DEFAULT_TIMEOUT_MS = 30_000;
    public static final long MAX_TIMEOUT_MS = 120_000;
    public static final String REASON_EXCEEDED = "OPERATION_BUDGET_EXCEEDED";
    public static final String REASON_INTERRUPTED = "OPERATION_INTERRUPTED";
    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();

    private final LongSupplier nanoClock;
    private final long startedAt, durationNanos;
    private final CollectionBudget local, parent;

    public static CollectionBudget start(Duration duration) { return new CollectionBudget(duration, System::nanoTime); }

    /** Explicit monotonic clock for deterministic tests; no wall-clock timestamps or identity are stored here. */
    public CollectionBudget(Duration duration, LongSupplier nanoClock) {
        if (duration == null || duration.isZero() || duration.isNegative() || duration.compareTo(Duration.ofMillis(MAX_TIMEOUT_MS)) > 0) {
            throw new IllegalArgumentException("Collection timeout must be positive and at most 120000 ms");
        }
        this.nanoClock = Objects.requireNonNull(nanoClock, "Monotonic clock is required");
        this.startedAt = nanoClock.getAsLong();
        this.durationNanos = duration.toNanos();
        this.local = null;
        this.parent = null;
    }

    private CollectionBudget(CollectionBudget local, CollectionBudget parent) {
        this.local = local; this.parent = parent;
        this.nanoClock = null; this.startedAt = 0; this.durationNanos = 0;
    }

    public static Scope open(String targetId, long timeoutMs) {
        return open(targetId, start(Duration.ofMillis(timeoutMs)));
    }

    public static Scope open(String targetId, CollectionBudget budget) {
        if (targetId == null || targetId.isBlank() || targetId.length() > 256) throw new IllegalArgumentException("Collection target is required");
        Objects.requireNonNull(budget, "Collection budget is required");
        Scope previous = ACTIVE.get();
        if (previous != null && !previous.targetId.equals(targetId)) throw targetMismatch();
        CollectionBudget effective = previous == null ? budget : new CollectionBudget(budget, previous.budget);
        Scope scope = new Scope(targetId, effective, previous, Thread.currentThread());
        ACTIVE.set(scope);
        return scope;
    }

    public static CollectionBudget current() { Scope scope = ACTIVE.get(); return scope == null ? null : scope.budget; }

    public static void checkpointCurrent() {
        CollectionBudget budget = current();
        if (budget != null) budget.checkpoint();
        else if (Thread.currentThread().isInterrupted()) throw new Aborted(REASON_INTERRUPTED);
    }

    public static void checkpoint(String targetId) {
        Scope scope = ACTIVE.get();
        if (scope != null && !scope.targetId.equals(targetId)) throw targetMismatch();
        checkpointCurrent();
    }

    public Duration remaining() {
        if (Thread.currentThread().isInterrupted()) return Duration.ZERO;
        if (parent != null) {
            Duration a = local.remaining(), b = parent.remaining();
            return a.compareTo(b) <= 0 ? a : b;
        }
        long elapsed = nanoClock.getAsLong() - startedAt;
        return elapsed < 0 || elapsed >= durationNanos ? Duration.ZERO : Duration.ofNanos(durationNanos - elapsed);
    }

    public boolean exhausted() { return remaining().isZero(); }
    public String reason() { return Thread.currentThread().isInterrupted() ? REASON_INTERRUPTED : REASON_EXCEEDED; }
    public Duration cap(Duration limit) {
        if (limit == null || limit.isZero() || limit.isNegative()) throw new IllegalArgumentException("Positive timeout is required");
        Duration remaining = remaining();
        return remaining.compareTo(limit) < 0 ? remaining : limit;
    }
    public void checkpoint() { if (exhausted()) throw new Aborted(reason()); }

    public static final class Aborted extends RuntimeException {
        private final String reason;
        private Aborted(String reason) { super(reason); this.reason = reason; }
        public String reason() { return reason; }
    }

    public static final class Scope implements AutoCloseable {
        private final String targetId;
        private final CollectionBudget budget;
        private final Scope previous;
        private final Thread owner;
        private boolean closed;
        private Scope(String targetId, CollectionBudget budget, Scope previous, Thread owner) {
            this.targetId = targetId; this.budget = budget; this.previous = previous; this.owner = owner;
        }
        public CollectionBudget budget() { return budget; }
        @Override public void close() {
            if (Thread.currentThread() != owner) throw new IllegalStateException("Collection scope belongs to its opening thread");
            if (closed) return;
            if (ACTIVE.get() != this) throw new IllegalStateException("Collection scopes must close in reverse order");
            if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
            closed = true;
        }
    }

    private static IllegalStateException targetMismatch() { return new IllegalStateException("Collection scope target mismatch"); }
}
