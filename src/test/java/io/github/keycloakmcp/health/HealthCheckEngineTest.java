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
    void healthyWithUnknownStaysHealthy() {
        assertThat(HealthCheckEngine.computeOverall(List.of(
                        result("a", HealthStatus.HEALTHY),
                        result("b", HealthStatus.UNKNOWN))))
                .isEqualTo(HealthStatus.HEALTHY);
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
