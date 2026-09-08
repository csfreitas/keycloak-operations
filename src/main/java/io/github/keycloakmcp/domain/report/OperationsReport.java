package io.github.keycloakmcp.domain.report;

import java.time.Instant;
import java.util.List;

import io.github.keycloakmcp.domain.metrics.PerformanceSummary;
import io.github.keycloakmcp.domain.platform.HealthCheckDetail;
import io.github.keycloakmcp.domain.platform.SnapshotDetail;

/** Sanitized operational observations over a collection window for one registered target. */
public record OperationsReport(
        String schemaVersion,
        String reportId,
        String targetId,
        String targetDisplayName,
        String productType,
        String environment,
        String configuredInfrastructureType,
        Instant generatedAt,
        ReportStatus status,
        List<ReportSection> sections,
        SnapshotDetail environmentSnapshot,
        HealthCheckDetail healthCheck,
        AssessmentReport assessment,
        PerformanceSummary performance,
        String markdown,
        ReportProvenance provenance) {

    /** Backward-compatible construction of schema 1.0 reports with unknown provenance. */
    public OperationsReport(String schemaVersion, String reportId, String targetId,
            String targetDisplayName, String productType, String environment,
            String configuredInfrastructureType, Instant generatedAt, ReportStatus status,
            List<ReportSection> sections, SnapshotDetail environmentSnapshot,
            HealthCheckDetail healthCheck, AssessmentReport assessment,
            PerformanceSummary performance, String markdown) {
        this(schemaVersion, reportId, targetId, targetDisplayName, productType, environment,
                configuredInfrastructureType, generatedAt, status, sections, environmentSnapshot,
                healthCheck, assessment, performance, markdown, null);
    }
}
