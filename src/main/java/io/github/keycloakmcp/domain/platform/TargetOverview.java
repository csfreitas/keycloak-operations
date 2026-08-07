package io.github.keycloakmcp.domain.platform;

import java.time.Instant;
import java.util.Map;

/**
 * Per-target operations overview for UI/REST.
 * <p>
 * Prefers persisted summaries (latest health, assessment, snapshot) over live
 * Admin/Prometheus chatter on page load.
 */
public record TargetOverview(
        String targetId,
        String displayName,
        String productType,
        String environment,
        boolean enabled,
        String keycloakUrl,
        String productVersion,
        String runtime,
        String namespace,
        Integer desiredReplicas,
        Integer readyReplicas,
        Integer podCount,
        Integer zoneCount,
        boolean metricsConfigured,
        HealthStatus healthStatus,
        AssessmentRunSummary latestAssessment,
        HealthCheckSummary latestHealthCheck,
        SnapshotSummary latestSnapshot,
        Instant updatedAt,
        Map<String, String> tags) {
}
