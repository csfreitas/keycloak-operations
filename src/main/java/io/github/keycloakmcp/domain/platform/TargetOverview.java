package io.github.keycloakmcp.domain.platform;

import java.time.Instant;
import java.util.Map;

/**
 * Per-target operations overview for UI/REST.
 */
public record TargetOverview(
        String targetId,
        String displayName,
        String productType,
        String environment,
        boolean enabled,
        String keycloakUrl,
        HealthStatus healthStatus,
        AssessmentRunSummary latestAssessment,
        HealthCheckSummary latestHealthCheck,
        SnapshotSummary latestSnapshot,
        Instant updatedAt,
        Map<String, String> tags) {
}
