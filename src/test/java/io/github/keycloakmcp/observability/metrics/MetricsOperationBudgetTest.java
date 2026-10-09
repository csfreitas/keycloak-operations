package io.github.keycloakmcp.observability.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.config.MetricsConfig;
import io.github.keycloakmcp.collection.CollectionBudget;

class MetricsOperationBudgetTest {
    @Test void inheritedDeadlineCapsEveryNewMetricsBudgetWithoutRenewal() {
        var clock = new AtomicLong();
        var parent = new CollectionBudget(Duration.ofMillis(100), clock::get);
        try (var scope = CollectionBudget.open("target-a", parent)) {
            var first = new MetricsOperationBudget(Duration.ofSeconds(30), clock::get);
            clock.set(Duration.ofMillis(90).toNanos());
            var nested = new MetricsOperationBudget(Duration.ofSeconds(30), clock::get);
            assertThat(first.remaining()).isEqualTo(Duration.ofMillis(10));
            assertThat(nested.remaining()).isEqualTo(Duration.ofMillis(10));
            clock.set(Duration.ofMillis(100).toNanos());
            assertThat(first.exhausted()).isTrue();
            assertThat(nested.exhausted()).isTrue();
            assertThat(nested.reason()).isEqualTo(CollectionBudget.REASON_EXCEEDED);
        }
        assertThat(CollectionBudget.current()).isNull();
    }

    @Test void localMetricsLimitStillWinsOverLongerParent() {
        var clock = new AtomicLong();
        try (var scope = CollectionBudget.open("target-a", new CollectionBudget(Duration.ofSeconds(60), clock::get))) {
            var metrics = new MetricsOperationBudget(Duration.ofSeconds(30), clock::get);
            clock.set(Duration.ofSeconds(30).toNanos());
            assertThat(metrics.exhausted()).isTrue();
            assertThat(scope.budget().exhausted()).isFalse();
        }
    }

    @Test void capturedParentSurvivesScopeExitRatherThanLookingUpTheCallbackThreadScope() {
        var parentClock = new AtomicLong();
        var localClock = new AtomicLong();
        MetricsOperationBudget captured;
        try (var scope = CollectionBudget.open("target-a", new CollectionBudget(Duration.ofMillis(100), parentClock::get))) {
            captured = new MetricsOperationBudget(Duration.ofSeconds(30), localClock::get);
        }
        parentClock.set(Duration.ofMillis(100).toNanos());
        try (var unrelated = CollectionBudget.open("target-b", new CollectionBudget(Duration.ofSeconds(30), localClock::get))) {
            assertThat(captured.exhausted()).isTrue();
            assertThat(new MetricsOperationBudget(Duration.ofSeconds(30), localClock::get).remaining())
                    .isEqualTo(Duration.ofSeconds(30));
        }
        assertThat(CollectionBudget.current()).isNull();
    }

    @Test void inheritedSubMillisecondDeadlineIsNeverRoundedUp() {
        var clock = new AtomicLong();
        try (var scope = CollectionBudget.open("target-a", new CollectionBudget(Duration.ofMillis(1), clock::get))) {
            var metrics = new MetricsOperationBudget(Duration.ofSeconds(30), clock::get);
            clock.set(999_999);
            assertThat(metrics.cap(Duration.ofSeconds(5))).isEqualTo(Duration.ofNanos(1));
            clock.incrementAndGet();
            assertThat(metrics.cap(Duration.ofSeconds(5))).isZero();
        }
    }

    @Test void elapsedTimeIsSharedAndExpirationNeverRenewsIt() {
        var clock = new AtomicLong(123);
        var budget = new MetricsOperationBudget(Duration.ofMillis(100), clock::get);
        assertThat(budget.remaining()).isEqualTo(Duration.ofMillis(100));
        clock.addAndGet(Duration.ofMillis(75).toNanos());
        assertThat(budget.remaining()).isEqualTo(Duration.ofMillis(25));
        clock.addAndGet(Duration.ofMillis(25).toNanos());
        assertThat(budget.exhausted()).isTrue();
        assertThat(budget.remaining()).isZero();
        clock.incrementAndGet();
        assertThat(budget.remaining()).isZero();
        assertThat(budget.reason()).isEqualTo("OPERATION_BUDGET_EXCEEDED");
    }

    @Test void capsTimeoutWithoutRoundingShortRemainingTimeUp() {
        var clock = new AtomicLong();
        var budget = new MetricsOperationBudget(Duration.ofMillis(100), clock::get);
        assertThat(budget.cap(Duration.ofMillis(20))).isEqualTo(Duration.ofMillis(20));
        clock.set(Duration.ofMillis(99).toNanos() + 999_999);
        assertThat(budget.cap(Duration.ofSeconds(1))).isEqualTo(Duration.ofNanos(1));
        clock.incrementAndGet();
        assertThat(budget.cap(Duration.ofSeconds(1))).isZero();
    }

    @Test void monotonicClockWrapDoesNotRenewTheBudget() {
        var clock = new AtomicLong(Long.MAX_VALUE - 2);
        var budget = new MetricsOperationBudget(Duration.ofNanos(10), clock::get);
        clock.addAndGet(5);
        assertThat(budget.remaining()).isEqualTo(Duration.ofNanos(5));
        clock.addAndGet(5);
        assertThat(budget.exhausted()).isTrue();
    }

    @Test void backwardsTestClockFailsClosed() {
        var clock = new AtomicLong(100);
        var budget = new MetricsOperationBudget(Duration.ofSeconds(1), clock::get);
        clock.set(99);
        assertThat(budget.exhausted()).isTrue();
    }

    @Test void interruptIsObservedWithoutClearingIt() {
        var budget = MetricsOperationBudget.start(Duration.ofSeconds(1));
        try {
            Thread.currentThread().interrupt();
            assertThat(budget.exhausted()).isTrue();
            assertThat(budget.remaining()).isZero();
            assertThat(budget.cap(Duration.ofSeconds(1))).isZero();
            assertThat(budget.reason()).isEqualTo("OPERATION_INTERRUPTED");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // Test isolation only; production never consumes the signal.
        }
    }

    @Test void distinctOperationsDoNotShareElapsedTime() {
        var clock = new AtomicLong();
        var first = new MetricsOperationBudget(Duration.ofMillis(100), clock::get);
        clock.set(Duration.ofMillis(100).toNanos());
        var second = new MetricsOperationBudget(Duration.ofMillis(100), clock::get);
        assertThat(first.exhausted()).isTrue();
        assertThat(second.remaining()).isEqualTo(Duration.ofMillis(100));
    }

    @Test void invalidAndExcessiveBudgetsAreRejected() {
        for (Duration duration : new Duration[]{null, Duration.ZERO, Duration.ofNanos(-1), Duration.ofMillis(120001), Duration.ofSeconds(Long.MAX_VALUE)}) {
            assertThatThrownBy(() -> MetricsOperationBudget.start(duration)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(MetricsOperationBudget.start(Duration.ofMillis(120000)).exhausted()).isFalse();
    }

    @Test void configurationIsValidatedEvenOutsideBeanValidation() {
        var config = mock(MetricsConfig.class);
        for (int invalid : new int[]{0, -1, 120001, Integer.MAX_VALUE}) {
            when(config.operationTimeoutMs()).thenReturn(invalid);
            assertThatThrownBy(() -> MetricsOperationBudget.fromConfig(config)).isInstanceOf(IllegalArgumentException.class);
        }
        when(config.operationTimeoutMs()).thenReturn(30000);
        assertThat(MetricsOperationBudget.fromConfig(config).remaining()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(30));
    }

    @Test void invalidTransportLimitsAreRejected() {
        var budget = MetricsOperationBudget.start(Duration.ofSeconds(1));
        for (Duration duration : new Duration[]{null, Duration.ZERO, Duration.ofMillis(-1)}) {
            assertThatThrownBy(() -> budget.cap(duration)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
