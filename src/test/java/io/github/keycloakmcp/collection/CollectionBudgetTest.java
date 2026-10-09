package io.github.keycloakmcp.collection;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CollectionBudgetTest {
    @AfterEach void noScopeOrInterruptLeaks() {
        assertThat(CollectionBudget.current()).isNull();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test void monotonicElapsedTimeAndSubMillisecondCapNeverRenew() {
        AtomicLong clock = new AtomicLong();
        var budget = new CollectionBudget(Duration.ofMillis(10), clock::get);
        clock.set(9_999_999);
        assertThat(budget.cap(Duration.ofSeconds(1))).isEqualTo(Duration.ofNanos(1));
        clock.incrementAndGet();
        assertThat(budget.remaining()).isZero();
        assertThatThrownBy(budget::checkpoint).isInstanceOf(CollectionBudget.Aborted.class).hasMessage("OPERATION_BUDGET_EXCEEDED").hasNoCause();
    }

    @Test void boundsAndInvalidTimeoutsFailClosed() {
        for (Duration value : new Duration[]{null, Duration.ZERO, Duration.ofNanos(-1), Duration.ofMillis(120001), Duration.ofSeconds(Long.MAX_VALUE)}) {
            assertThatThrownBy(() -> CollectionBudget.start(value)).isInstanceOf(IllegalArgumentException.class);
        }
        var budget = CollectionBudget.start(Duration.ofMillis(120000));
        for (Duration value : new Duration[]{null, Duration.ZERO, Duration.ofNanos(-1)}) {
            assertThatThrownBy(() -> budget.cap(value)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(budget.exhausted()).isFalse();
    }

    @Test void nanoClockWrapIsSafeAndBackwardsClockDoesNotGrantMoreTime() {
        AtomicLong clock = new AtomicLong(Long.MAX_VALUE - 2);
        var budget = new CollectionBudget(Duration.ofNanos(10), clock::get);
        clock.addAndGet(5);
        assertThat(budget.remaining()).isEqualTo(Duration.ofNanos(5));
        clock.addAndGet(5);
        assertThat(budget.exhausted()).isTrue();
        AtomicLong backward = new AtomicLong(100);
        var other = new CollectionBudget(Duration.ofSeconds(1), backward::get);
        backward.set(99);
        assertThat(other.exhausted()).isTrue();
    }

    @Test void scopeRestoresOuterBudgetAfterExceptionalExit() {
        var outerBudget = CollectionBudget.start(Duration.ofSeconds(2));
        try (var outer = CollectionBudget.open("a", outerBudget)) {
            assertThat(CollectionBudget.current()).isSameAs(outerBudget);
            assertThatThrownBy(() -> {
                try (var inner = CollectionBudget.open("a", 1000)) {
                    assertThat(CollectionBudget.current()).isSameAs(inner.budget());
                    throw new IllegalArgumentException("fixture");
                }
            }).isInstanceOf(IllegalArgumentException.class);
            assertThat(CollectionBudget.current()).isSameAs(outerBudget);
        }
    }

    @Test void nestedBudgetsCanShortenButCannotExtendParentDeadline() {
        AtomicLong clock = new AtomicLong();
        var parent = new CollectionBudget(Duration.ofMillis(100), clock::get);
        try (var outer = CollectionBudget.open("a", parent)) {
            clock.set(90_000_000);
            try (var inner = CollectionBudget.open("a", new CollectionBudget(Duration.ofSeconds(1), clock::get))) {
                assertThat(inner.budget().remaining()).isEqualTo(Duration.ofMillis(10));
                clock.set(100_000_000);
                assertThat(inner.budget().exhausted()).isTrue();
            }
            assertThat(parent.exhausted()).isTrue();
        }
    }

    @Test void shorterNestedDeadlineDoesNotPoisonStillValidParent() {
        AtomicLong clock = new AtomicLong();
        var parent = new CollectionBudget(Duration.ofSeconds(1), clock::get);
        try (var outer = CollectionBudget.open("a", parent)) {
            try (var child = CollectionBudget.open("a", new CollectionBudget(Duration.ofMillis(100), clock::get))) {
                clock.set(100_000_000);
                assertThat(child.budget().exhausted()).isTrue();
            }
            assertThat(CollectionBudget.current()).isSameAs(parent);
            assertThat(parent.remaining()).isEqualTo(Duration.ofMillis(900));
        }
    }

    @Test void differentTargetCannotReuseOrReplaceActiveScope() {
        try (var scope = CollectionBudget.open("a", 1000)) {
            assertThatThrownBy(() -> CollectionBudget.open("b", 1000)).isInstanceOf(IllegalStateException.class).hasMessage("Collection scope target mismatch");
            assertThatThrownBy(() -> CollectionBudget.checkpoint("b")).isInstanceOf(IllegalStateException.class).hasMessage("Collection scope target mismatch");
            assertThat(CollectionBudget.current()).isSameAs(scope.budget());
            CollectionBudget.checkpoint("a");
        }
    }

    @Test void scopeIsNotInheritedButCapturedBudgetStillConstrainsTransportThreads() throws Exception {
        AtomicLong clock = new AtomicLong();
        var budget = new CollectionBudget(Duration.ofMillis(100), clock::get);
        try (var scope = CollectionBudget.open("a", budget); var executor = Executors.newSingleThreadExecutor()) {
            assertThat(executor.submit(CollectionBudget::current).get(1, TimeUnit.SECONDS)).isNull();
            clock.set(100_000_000);
            assertThat(executor.submit(() -> scope.budget().exhausted()).get(1, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.submit(() -> {
                try (var other = CollectionBudget.open("b", 1000)) { return !other.budget().exhausted(); }
            }).get(1, TimeUnit.SECONDS)).isTrue();
            assertThat(CollectionBudget.current()).isSameAs(budget);
        }
    }

    @Test void interruptIsPreservedAndHasFixedReason() {
        try (var scope = CollectionBudget.open("a", 1000)) {
            Thread.currentThread().interrupt();
            assertThat(scope.budget().remaining()).isZero();
            assertThatThrownBy(CollectionBudget::checkpointCurrent).isInstanceOf(CollectionBudget.Aborted.class).hasMessage("OPERATION_INTERRUPTED");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
    }

    @Test void outOfOrderCloseDoesNotDiscardInnerScope() {
        var outer = CollectionBudget.open("a", 1000);
        var inner = CollectionBudget.open("a", 1000);
        try {
            assertThatThrownBy(outer::close).isInstanceOf(IllegalStateException.class);
            assertThat(CollectionBudget.current()).isSameAs(inner.budget());
        } finally { inner.close(); outer.close(); outer.close(); }
    }

    @Test void invalidScopeDoesNotCreateThreadState() {
        assertThatThrownBy(() -> CollectionBudget.open(null, 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CollectionBudget.open(" ", 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CollectionBudget.open("a", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CollectionBudget.open("a", (CollectionBudget) null)).isInstanceOf(NullPointerException.class);
    }
}
