package io.github.keycloakmcp.domain.report;

import java.time.Instant;
import java.util.List;

import io.github.keycloakmcp.domain.metrics.PerformanceSummary;
import io.github.keycloakmcp.domain.platform.HealthCheckDetail;
import io.github.keycloakmcp.domain.platform.SnapshotDetail;

/** Sanitized point-in-time operational report for one registered target. */
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
        String markdown) {
}
