package io.github.keycloakmcp.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.target.InfrastructureTargetConfiguration;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetType;
import jakarta.enterprise.inject.Instance;

class HealthCheckEngineTest {

    @Test
    void deadlineRejectsLateSuccessAndDoesNotStartNextCheck() {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var budget = new io.github.keycloakmcp.collection.CollectionBudget(java.time.Duration.ofSeconds(1), clock::get);
        HealthCheck slow = org.mockito.Mockito.mock(HealthCheck.class);
        HealthCheck next = org.mockito.Mockito.mock(HealthCheck.class);
        org.mockito.Mockito.when(slow.name()).thenReturn("slow");
        org.mockito.Mockito.when(next.name()).thenReturn("next");
        org.mockito.Mockito.when(slow.check(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            clock.set(1_000_000_000L);
            return result("slow", HealthStatus.HEALTHY);
        });
        try (var scope = io.github.keycloakmcp.collection.CollectionBudget.open("t1", budget)) {
            var run = new HealthCheckEngine(instanceOf(List.of(slow, next))).run(sampleTarget(false));
            assertThat(run.overallStatus()).isEqualTo(HealthStatus.UNKNOWN);
            assertThat(run.results()).hasSize(2).allSatisfy(r -> assertThat(r.details())
                    .containsEntry("collectionComplete", false).containsEntry("reasonCode", "OPERATION_BUDGET_EXCEEDED"));
            org.mockito.Mockito.verify(next, org.mockito.Mockito.never()).check(org.mockito.ArgumentMatchers.any());
            assertThat(io.github.keycloakmcp.collection.CollectionBudget.current()).isSameAs(scope.budget());
        }
        assertThat(io.github.keycloakmcp.collection.CollectionBudget.current()).isNull();
    }

    @Test
    void explicitPartialRetainsEarlierCriticalObservationAfterDeadline() {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var budget = new io.github.keycloakmcp.collection.CollectionBudget(java.time.Duration.ofSeconds(1), clock::get);
        HealthCheck check = org.mockito.Mockito.mock(HealthCheck.class);
        org.mockito.Mockito.when(check.check(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            clock.set(1_000_000_000L);
            return HealthComponentResult.of("management", HealthStatus.CRITICAL, "Previously observed DOWN",
                    Map.of("collectionComplete", false, "reasonCode", "OPERATION_BUDGET_EXCEEDED"), 1);
        });
        try (var scope = io.github.keycloakmcp.collection.CollectionBudget.open("t1", budget)) {
            assertThat(new HealthCheckEngine(instanceOf(List.of(check))).run(sampleTarget(false)).overallStatus())
                    .isEqualTo(HealthStatus.CRITICAL);
        }
    }

    @Test
    void interruptedCollectionDoesNotStartChecksOrConsumeFlag() {
        HealthCheck check = org.mockito.Mockito.mock(HealthCheck.class);
        org.mockito.Mockito.when(check.name()).thenReturn("not-started");
        Thread.currentThread().interrupt();
        try {
            var run = new HealthCheckEngine(instanceOf(List.of(check))).run(sampleTarget(false));
            assertThat(run.results().getFirst().details()).containsEntry("reasonCode", "OPERATION_INTERRUPTED");
            org.mockito.Mockito.verify(check, org.mockito.Mockito.never()).check(org.mockito.ArgumentMatchers.any());
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        assertThat(io.github.keycloakmcp.collection.CollectionBudget.current()).isNull();
    }

    @Test
    void overallCriticalWhenAnyCheckCritical() {
        HealthCheckEngine engine = engineWith(
                result("a", HealthStatus.HEALTHY),
                result("b", HealthStatus.CRITICAL),
                result("c", HealthStatus.UNKNOWN));

        HealthCheckEngine.HealthRunResult run = engine.run(sampleTarget(false));

        assertThat(run.overallStatus()).isEqualTo(HealthStatus.CRITICAL);
        assertThat(run.results()).hasSize(3);
        assertThat(run.componentStatuses()).containsEntry("b", "CRITICAL");
    }

    @Test
    void unknownAloneDoesNotMakeCritical() {
        HealthCheckEngine engine = engineWith(
                result("a", HealthStatus.UNKNOWN),
                result("b", HealthStatus.UNKNOWN));

        assertThat(engine.run(sampleTarget(false)).overallStatus()).isEqualTo(HealthStatus.UNKNOWN);
    }

    @Test
    void healthyWithUnknownIsInconclusiveInEitherOrder() {
        assertThat(HealthCheckEngine.computeOverall(List.of(
                        result("a", HealthStatus.HEALTHY),
                        result("b", HealthStatus.UNKNOWN))))
                .isEqualTo(HealthStatus.UNKNOWN);
        assertThat(HealthCheckEngine.computeOverall(List.of(
                        result("b", HealthStatus.UNKNOWN),
                        result("a", HealthStatus.HEALTHY))))
                .isEqualTo(HealthStatus.UNKNOWN);
    }

    @Test
    void everyStatusPairHasOrderIndependentSeverity() {
        List<HealthStatus> severity = List.of(HealthStatus.HEALTHY, HealthStatus.UNKNOWN,
                HealthStatus.WARNING, HealthStatus.CRITICAL);
        for (HealthStatus first : severity) {
            for (HealthStatus second : severity) {
                HealthStatus expected = severity.get(Math.max(severity.indexOf(first), severity.indexOf(second)));
                assertThat(HealthCheckEngine.computeOverall(List.of(result("a", first), result("b", second))))
                        .as("%s and %s", first, second).isEqualTo(expected);
            }
        }
    }

    @Test
    void absentResultsAndStatusesAreInconclusive() {
        assertThat(HealthCheckEngine.computeOverall(List.of())).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(HealthCheckEngine.computeOverall(null)).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(HealthCheckEngine.computeOverall(java.util.Arrays.asList(result("a", HealthStatus.HEALTHY), null)))
                .isEqualTo(HealthStatus.UNKNOWN);
        assertThat(HealthCheckEngine.computeOverall(List.of(new HealthComponentResult("a", null, "", Map.of(), 0))))
                .isEqualTo(HealthStatus.UNKNOWN);
    }

    @Test
    void unexpectedExceptionIsUnknownAndDoesNotExposeMessageOrThrowableInLogs() {
        String canary = "provider-secret-canary";
        HealthCheck failing = org.mockito.Mockito.mock(HealthCheck.class);
        org.mockito.Mockito.when(failing.name()).thenReturn("failure");
        org.mockito.Mockito.when(failing.check(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException(canary, new IllegalArgumentException(canary)));
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(HealthCheckEngine.class.getName());
        List<java.util.logging.LogRecord> records = new java.util.ArrayList<>();
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { records.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(handler);
        try {
            var run = new HealthCheckEngine(instanceOf(List.of(failing))).run(sampleTarget(false));
            assertThat(run.overallStatus()).isEqualTo(HealthStatus.UNKNOWN);
            assertThat(run.results().getFirst().details()).containsEntry("reasonCode", "CHECK_FAILED");
            assertThat(run.toString()).doesNotContain(canary);
            assertThat(records).isNotEmpty().allSatisfy(record -> {
                assertThat(record.getThrown()).isNull();
                assertThat(record.getMessage()).doesNotContain(canary);
            });
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void nullCheckResultIsInconclusiveAlongsideHealthyCheck() {
        HealthCheck unavailable = org.mockito.Mockito.mock(HealthCheck.class);
        org.mockito.Mockito.when(unavailable.name()).thenReturn("missing");
        HealthCheck healthy = org.mockito.Mockito.mock(HealthCheck.class);
        org.mockito.Mockito.when(healthy.check(org.mockito.ArgumentMatchers.any())).thenReturn(result("ok", HealthStatus.HEALTHY));
        var run = new HealthCheckEngine(instanceOf(List.of(healthy, unavailable))).run(sampleTarget(false));
        assertThat(run.overallStatus()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(run.results().getLast().details()).containsEntry("reasonCode", "CHECK_RESULT_UNAVAILABLE");
    }

    @Test
    void warningBeatsHealthy() {
        assertThat(HealthCheckEngine.computeOverall(List.of(
                        result("a", HealthStatus.HEALTHY),
                        result("b", HealthStatus.WARNING))))
                .isEqualTo(HealthStatus.WARNING);
    }

    private static HealthCheckEngine engineWith(HealthComponentResult... results) {
        List<HealthCheck> checks = new java.util.ArrayList<>();
        for (HealthComponentResult r : results) {
            checks.add(new HealthCheck() {
                @Override
                public String name() {
                    return r.name();
                }

                @Override
                public HealthComponentResult check(Target target) {
                    return r;
                }
            });
        }
        return new HealthCheckEngine(instanceOf(checks));
    }

    private static HealthComponentResult result(String name, HealthStatus status) {
        return HealthComponentResult.of(name, status, status.name(), Map.of(), 1L);
    }

    private static Target sampleTarget(boolean withInfra) {
        return new Target(
                TargetId.of("t1"),
                "Test",
                TargetType.KEYCLOAK,
                TargetEnvironment.DEV,
                true,
                new KeycloakTargetConfiguration("http://localhost", "master", "client", "ref"),
                withInfra
                        ? new InfrastructureTargetConfiguration(
                                InfrastructureType.KUBERNETES, "cluster", "ns", "cred")
                        : null,
                null,
                Map.of());
    }

    @SuppressWarnings("unchecked")
    private static Instance<HealthCheck> instanceOf(List<HealthCheck> checks) {
        Instance<HealthCheck> instance = org.mockito.Mockito.mock(Instance.class);
        org.mockito.Mockito.when(instance.iterator()).thenAnswer(inv -> checks.iterator());
        return instance;
    }
}
