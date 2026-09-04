package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.assessment.engine.AssessmentConfidence;
import io.github.keycloakmcp.assessment.engine.AssessmentResult;
import io.github.keycloakmcp.assessment.engine.AssessmentScope;
import io.github.keycloakmcp.assessment.engine.AssessmentStatus;
import io.github.keycloakmcp.assessment.engine.Finding;
import io.github.keycloakmcp.assessment.engine.FindingStatus;
import io.github.keycloakmcp.assessment.engine.Severity;
import io.github.keycloakmcp.domain.platform.HealthCheckDetail;
import io.github.keycloakmcp.domain.platform.HealthCheckSummary;
import io.github.keycloakmcp.domain.platform.HealthComponentView;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.domain.platform.SnapshotDetail;
import io.github.keycloakmcp.domain.platform.SnapshotSummary;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.domain.report.ReportSectionStatus;
import io.github.keycloakmcp.domain.report.ReportStatus;
import io.github.keycloakmcp.domain.metrics.PerformanceSummary;
import io.github.keycloakmcp.observability.metrics.MetricWindow;
import io.github.keycloakmcp.observability.metrics.MetricsProviderStatus;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.target.InfrastructureTargetConfiguration;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.ObservabilityTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import io.github.keycloakmcp.target.TargetType;

class OperationsReportServiceTest {

    private TargetResolver targetResolver;
    private TargetAuthorizationService authorization;
    private SnapshotService snapshotService;
    private HealthCheckService healthService;
    private AssessmentHistoryService assessmentService;
    private MetricsService metricsService;
    private OperationsReportService service;

    @BeforeEach
    void setUp() {
        targetResolver = mock(TargetResolver.class);
        authorization = mock(TargetAuthorizationService.class);
        snapshotService = mock(SnapshotService.class);
        healthService = mock(HealthCheckService.class);
        assessmentService = mock(AssessmentHistoryService.class);
        metricsService = mock(MetricsService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new OperationsReportService(
                targetResolver,
                authorization,
                snapshotService,
                healthService,
                assessmentService,
                metricsService,
                new SensitiveDataFilter(objectMapper),
                objectMapper);
    }

    @Test
    void generatesCompleteSanitizedReportFromSharedServices() {
        Instant now = Instant.parse("2026-09-04T10:00:00Z");
        Target target = target();
        when(targetResolver.require("rhbk-prd")).thenReturn(target);
        SnapshotSummary snapshotSummary = new SnapshotSummary("snap-1", "rhbk-prd", "hash", now);
        when(snapshotService.create("rhbk-prd")).thenReturn(snapshotSummary);
        when(snapshotService.getDetail("rhbk-prd", "snap-1")).thenReturn(new SnapshotDetail(
                "snap-1",
                "rhbk-prd",
                "hash",
                now,
                Map.of(
                        "inventory", Map.of("runtime", "OPENSHIFT", "namespace", "sso"),
                        "keycloakUrl", "https://internal.example.test",
                        "clientSecret", "hidden")));
        HealthCheckSummary healthSummary =
                new HealthCheckSummary("health-1", "rhbk-prd", HealthStatus.HEALTHY, TriggerType.MCP, now, now, now);
        when(healthService.run("rhbk-prd", TriggerType.MCP)).thenReturn(healthSummary);
        when(healthService.get("rhbk-prd", "health-1")).thenReturn(new HealthCheckDetail(
                "health-1",
                "rhbk-prd",
                HealthStatus.HEALTHY,
                TriggerType.MCP,
                now,
                now,
                now,
                List.of(new HealthComponentView("admin-api", HealthStatus.HEALTHY, "Reachable", 12L, Map.of())),
                Map.of()));
        when(assessmentService.runAndPersist("rhbk-prd", "keycloak-production", TriggerType.MCP))
                .thenReturn(assessment(now, AssessmentStatus.COMPLETE));

        var report = service.generate("rhbk-prd", "keycloak-production", "15m", TriggerType.MCP);

        assertThat(report.status()).isEqualTo(ReportStatus.COMPLETE);
        assertThat(report.healthCheck().overallStatus()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(report.assessment().findings()).hasSize(1);
        assertThat(report.environmentSnapshot().summary()).containsEntry("clientSecret", "[REDACTED]");
        assertThat(report.environmentSnapshot().summary()).doesNotContainKey("keycloakUrl");
        assertThat(report.markdown()).contains("# Keycloak / RHBK Operations Report", "RHBK-HA-001");
        assertThat(report.markdown()).doesNotContain("hidden");
        assertThat(report.markdown()).doesNotContain("internal.example.test");
        assertThat(report.sections()).anySatisfy(section -> {
            assertThat(section.name()).isEqualTo("performance");
            assertThat(section.status()).isEqualTo(ReportSectionStatus.SKIPPED);
        });
        verify(authorization).assertAllowed(target, TargetPermission.ASSESS);
    }

    @Test
    void keepsUsefulResultsAndMarksReportPartialWhenPlatformCollectionFails() {
        Instant now = Instant.parse("2026-09-04T10:00:00Z");
        when(targetResolver.require("rhbk-prd")).thenReturn(target());
        when(snapshotService.create("rhbk-prd")).thenThrow(new IllegalStateException("token=must-not-leak"));
        when(healthService.run("rhbk-prd", TriggerType.API)).thenReturn(
                new HealthCheckSummary("health-1", "rhbk-prd", HealthStatus.WARNING, TriggerType.API, now, now, now));
        when(healthService.get("rhbk-prd", "health-1")).thenReturn(new HealthCheckDetail(
                "health-1", "rhbk-prd", HealthStatus.WARNING, TriggerType.API, now, now, now, List.of(), Map.of()));
        when(assessmentService.runAndPersist("rhbk-prd", null, TriggerType.API))
                .thenReturn(assessment(now, AssessmentStatus.PARTIAL));

        var report = service.generate("rhbk-prd", null, null, TriggerType.API);

        assertThat(report.status()).isEqualTo(ReportStatus.PARTIAL);
        assertThat(report.environmentSnapshot()).isNull();
        assertThat(report.healthCheck()).isNotNull();
        assertThat(report.sections()).anySatisfy(section -> {
            if (section.name().equals("platform")) {
                assertThat(section.status()).isEqualTo(ReportSectionStatus.FAILED);
                assertThat(section.message()).doesNotContain("must-not-leak");
            }
        });
        assertThat(report.markdown()).doesNotContain("must-not-leak");
    }

    @Test
    void marksPlatformPartialWhenInventoryContainsWarnings() {
        Instant now = Instant.parse("2026-09-04T10:00:00Z");
        when(targetResolver.require("rhbk-prd")).thenReturn(target());
        when(snapshotService.create("rhbk-prd"))
                .thenReturn(new SnapshotSummary("snap-1", "rhbk-prd", "hash", now));
        when(snapshotService.getDetail("rhbk-prd", "snap-1")).thenReturn(new SnapshotDetail(
                "snap-1",
                "rhbk-prd",
                "hash",
                now,
                Map.of("inventory", Map.of("runtime", "OPENSHIFT", "warnings", List.of("PDB not found")))));
        when(healthService.run("rhbk-prd", TriggerType.API)).thenReturn(
                new HealthCheckSummary("health-1", "rhbk-prd", HealthStatus.HEALTHY, TriggerType.API, now, now, now));
        when(healthService.get("rhbk-prd", "health-1")).thenReturn(new HealthCheckDetail(
                "health-1", "rhbk-prd", HealthStatus.HEALTHY, TriggerType.API, now, now, now, List.of(), Map.of()));
        when(assessmentService.runAndPersist("rhbk-prd", null, TriggerType.API))
                .thenReturn(assessment(now, AssessmentStatus.COMPLETE));

        var report = service.generate("rhbk-prd", null, null, TriggerType.API);

        assertThat(report.status()).isEqualTo(ReportStatus.PARTIAL);
        assertThat(report.sections()).anySatisfy(section -> {
            if (section.name().equals("platform")) {
                assertThat(section.status()).isEqualTo(ReportSectionStatus.PARTIAL);
            }
        });
    }

    @Test
    void keepsHealthSeparateFromDegradedMetricsCompleteness() {
        Instant now = Instant.parse("2026-09-04T10:00:00Z");
        Target target = targetWithMetrics();
        when(targetResolver.require("rhbk-prd")).thenReturn(target);
        when(snapshotService.create("rhbk-prd"))
                .thenReturn(new SnapshotSummary("snap-1", "rhbk-prd", "hash", now));
        when(snapshotService.getDetail("rhbk-prd", "snap-1"))
                .thenReturn(new SnapshotDetail("snap-1", "rhbk-prd", "hash", now, Map.of()));
        when(healthService.run("rhbk-prd", TriggerType.API)).thenReturn(
                new HealthCheckSummary("health-1", "rhbk-prd", HealthStatus.HEALTHY, TriggerType.API, now, now, now));
        when(healthService.get("rhbk-prd", "health-1")).thenReturn(new HealthCheckDetail(
                "health-1", "rhbk-prd", HealthStatus.HEALTHY, TriggerType.API, now, now, now, List.of(), Map.of()));
        when(assessmentService.runAndPersist("rhbk-prd", null, TriggerType.API))
                .thenReturn(assessment(now, AssessmentStatus.COMPLETE));
        when(metricsService.summary("rhbk-prd", "15m")).thenReturn(new PerformanceSummary(
                "rhbk-prd",
                MetricWindow.W_15M,
                MetricsProviderStatus.DEGRADED,
                "prometheus",
                now,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of()));

        var report = service.generate("rhbk-prd", null, "15m", TriggerType.API);

        assertThat(report.status()).isEqualTo(ReportStatus.PARTIAL);
        assertThat(report.healthCheck().overallStatus()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(report.performance().providerStatus()).isEqualTo(MetricsProviderStatus.DEGRADED);
        assertThat(report.sections()).anySatisfy(section -> {
            if (section.name().equals("performance")) {
                assertThat(section.status()).isEqualTo(ReportSectionStatus.PARTIAL);
            }
        });
    }

    @Test
    void marksReportFailedWhenNoCoreSectionProducesUsableEvidence() {
        when(targetResolver.require("rhbk-prd")).thenReturn(target());
        when(snapshotService.create("rhbk-prd")).thenThrow(new IllegalStateException("secret=platform-leak"));
        when(healthService.run("rhbk-prd", TriggerType.API))
                .thenThrow(new IllegalStateException("token=health-leak"));
        when(assessmentService.runAndPersist("rhbk-prd", null, TriggerType.API))
                .thenThrow(new IllegalStateException("password=assessment-leak"));

        var report = service.generate("rhbk-prd", null, null, TriggerType.API);

        assertThat(report.status()).isEqualTo(ReportStatus.FAILED);
        assertThat(report.environmentSnapshot()).isNull();
        assertThat(report.healthCheck()).isNull();
        assertThat(report.assessment()).isNull();
        assertThat(report.sections()).filteredOn(section -> !section.name().equals("performance"))
                .allMatch(section -> section.status() == ReportSectionStatus.FAILED);
        assertThat(report.markdown())
                .doesNotContain("platform-leak")
                .doesNotContain("health-leak")
                .doesNotContain("assessment-leak");
    }

    private static AssessmentResult assessment(Instant now, AssessmentStatus status) {
        Finding actionable = new Finding(
                "rhbk-prd",
                "RHBK-HA-001",
                "Single replica",
                "availability",
                Severity.HIGH,
                FindingStatus.FAIL,
                "Only one ready replica was observed.",
                Map.of("readyReplicas", 1),
                "Authentication availability is reduced.",
                "Run multiple replicas across failure domains.",
                List.of());
        Finding pass = new Finding(
                "rhbk-prd", "RHBK-TLS-001", "TLS", "security", Severity.INFO, FindingStatus.PASS,
                "TLS is configured.", Map.of(), null, null, List.of());
        return new AssessmentResult(
                "assessment-1",
                "rhbk-prd",
                "keycloak-production",
                AssessmentScope.target("rhbk-prd"),
                status,
                72,
                Map.of("availability", 60),
                status == AssessmentStatus.COMPLETE ? 100 : 70,
                AssessmentConfidence.MEDIUM,
                2,
                1,
                0,
                status == AssessmentStatus.COMPLETE ? 0 : 1,
                status == AssessmentStatus.COMPLETE ? List.of() : List.of("metrics"),
                List.of(actionable, pass),
                List.of(),
                now,
                now);
    }

    private static Target target() {
        return new Target(
                TargetId.of("rhbk-prd"),
                "RHBK Production",
                TargetType.RHBK,
                TargetEnvironment.PRD,
                true,
                new KeycloakTargetConfiguration("https://sso.example.test", "master", "operations", "cred-rhbk"),
                new InfrastructureTargetConfiguration(InfrastructureType.OPENSHIFT, "cluster-a", "sso", "kube-rhbk"),
                null,
                Map.of());
    }

    private static Target targetWithMetrics() {
        Target target = target();
        return new Target(
                target.id(),
                target.displayName(),
                target.type(),
                target.environment(),
                target.enabled(),
                target.keycloak(),
                target.infrastructure(),
                new ObservabilityTargetConfiguration(
                        "PROMETHEUS", null, "https://prometheus.example.test", "metrics-ref", "sso", "NAMESPACE"),
                target.tags());
    }
}
