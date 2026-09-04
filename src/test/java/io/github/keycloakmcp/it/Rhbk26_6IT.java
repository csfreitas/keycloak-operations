package io.github.keycloakmcp.it;

import static io.github.keycloakmcp.it.DisposableRhbkGuard.REALM;
import static io.github.keycloakmcp.it.DisposableRhbkGuard.TARGET_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.assessment.engine.AssessmentStatus;
import io.github.keycloakmcp.config.McpRuntimeConfig;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.domain.report.ReportSectionStatus;
import io.github.keycloakmcp.domain.report.ReportStatus;
import io.github.keycloakmcp.service.platform.AssessmentHistoryService;
import io.github.keycloakmcp.service.platform.OperationsReportService;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetBootstrapService;
import io.github.keycloakmcp.target.TargetResolver;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Genuine, opt-in read-only checks against a pre-provisioned disposable RHBK.
 * This test never creates clients, grants roles, or modifies the target.
 */
@QuarkusTest
@TestProfile(DisposableRhbkIntegrationTestProfile.class)
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "RUN_RHBK_IT", matches = "true")
class Rhbk26_6IT {
    @Inject TargetResolver targets;
    @Inject StableAdminApiAdapter adminApi;
    @Inject AssessmentHistoryService assessments;
    @Inject OperationsReportService reports;
    @Inject McpRuntimeConfig runtime;
    @Inject TargetAuthorizationService authorization;
    @Inject TargetBootstrapService targetBootstrap;
    @Inject ObjectMapper mapper;

    @Test
    void readsImportedRealmAndChecksVersionOnlyWhenAdminApiActuallyExposesIt() {
        Target target = fixture();
        var settings = DisposableRhbkGuard.requireTarget(target);
        var realm = adminApi.getRealm(target, REALM);
        assertThat(realm.getRealm()).isEqualTo(REALM);
        assertThat(adminApi.listClients(target, REALM, true)).isNotEmpty();
        String observedVersion = observableVersion(target);
        if (observedVersion != null) {
            assertThat(observedVersion).isEqualTo(settings.expectedVersion);
        }
        // Missing/forbidden serverinfo is deliberately not replaced by RHBK_EXPECTED_VERSION.
        // The second test requires the unknown/incomplete state to remain explicit in the report.
    }

    @Test
    void preservesReadableRealmEvidenceAndReportsUnavailableInfrastructureHonestly() throws Exception {
        Target target = fixture();
        var settings = DisposableRhbkGuard.requireTarget(target);
        assertThat(adminApi.getRealm(target, REALM).getRealm()).isEqualTo(REALM);
        boolean versionObserved = observableVersion(target) != null;
        Instant start = Instant.now();
        var assessment = assessments.runAndPersist(TARGET_ID, "rhbk-openshift-production", TriggerType.API);
        assertThat(assessment.id()).isNotBlank();
        assertThat(assessment.targetId()).isEqualTo(TARGET_ID);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.scoreAvailable()).isFalse();
        assertThat(assessment.evidenceCompleteness()).isLessThan(100);
        assertThat(assessment.missingEvidence()).isNotEmpty();
        assertThat(assessment.evidence()).anyMatch(e -> "keycloak".equals(e.source())
                && e.subject() != null && REALM.equals(e.subject().id()));
        if (!versionObserved) {
            assertThat(assessment.evidence()).noneMatch(e -> "keycloak.version".equals(e.key())
                    && e.value() != null && !String.valueOf(e.value()).isBlank()
                    && !"UNKNOWN".equalsIgnoreCase(String.valueOf(e.value())));
        }

        var report = reports.generate(TARGET_ID, "rhbk-openshift-production", null, TriggerType.API);
        assertThat(report.targetId()).isEqualTo(TARGET_ID);
        assertThat(report.productType()).isEqualTo("RHBK"); // Configured target type, not inferred API evidence.
        assertThat(report.configuredInfrastructureType()).isEqualTo("NONE");
        assertThat(report.status()).isEqualTo(ReportStatus.PARTIAL);
        assertThat(report.assessment()).isNotNull();
        assertThat(report.assessment().status()).isEqualTo("PARTIAL");
        assertThat(report.assessment().scoreAvailable()).isFalse();
        assertThat(report.performance()).isNull();
        assertThat(report.sections()).anyMatch(s -> "performance".equals(s.name())
                && s.status() == ReportSectionStatus.SKIPPED);
        assertThat(report.environmentSnapshot()).isNotNull();
        assertThat(report.environmentSnapshot().targetId()).isEqualTo(TARGET_ID);
        assertThat(report.provenance()).isNotNull();
        assertThat(report.provenance().collectionMode()).isEqualTo("INDEPENDENT_SECTION_COLLECTIONS");
        assertThat(report.provenance().retainedEvidenceReplayAvailable()).isFalse();
        assertThat(report.provenance().bundledRuleCatalogSha256()).matches("[a-f0-9]{64}");
        assertThat(report.provenance().collectionStartedAt()).isAfterOrEqualTo(start);
        assertThat(report.provenance().collectionCompletedAt())
                .isAfterOrEqualTo(report.provenance().collectionStartedAt()).isBeforeOrEqualTo(Instant.now());
        if (!versionObserved) {
            Object snapshotVersion = report.environmentSnapshot().summary().get("serverVersion");
            assertThat(snapshotVersion == null || "UNKNOWN".equalsIgnoreCase(String.valueOf(snapshotVersion)))
                    .as("An unavailable Admin REST version must not be invented from fixture configuration").isTrue();
        }
        // Boolean assertion avoids printing a real secret or full report if redaction regresses.
        assertThat(mapper.writeValueAsString(report).contains(settings.clientSecret))
                .as("The operations report must not disclose the fixture credential").isFalse();
    }

    private Target fixture() {
        assertThat(runtime.readOnly()).isTrue();
        assertThat(authorization.isLocalLab()).isTrue();
        Target target = targets.require(TARGET_ID);
        DisposableRhbkGuard.requireTarget(target);
        // Only the disposable platform database is written; target Admin REST remains read-only.
        // Configuration registry prevents a preexisting DB entry from changing the guarded endpoint.
        targetBootstrap.syncConfigTargetsToDatabase();
        return target;
    }

    private String observableVersion(Target target) {
        try {
            var info = adminApi.getServerInfo(target);
            String version = info == null || info.getSystemInfo() == null ? null : info.getSystemInfo().getVersion();
            return version == null || version.isBlank() ? null : version;
        } catch (McpException e) {
            // Only a known authorization limitation is acceptable. Bad credentials/outages must fail.
            assertThat(e.getCode()).isEqualTo(ErrorCode.AUTHORIZATION_FAILED);
            return null;
        }
    }
}
