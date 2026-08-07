package io.github.keycloakmcp.domain.platform;

import java.time.Instant;
import java.util.Map;

/**
 * Fleet dashboard row combining target metadata with latest persisted signals.
 * <p>
 * Built from registry + latest health/assessment/snapshot rows — does not run
 * live health, assessment, or Prometheus queries on page load.
 */
public record FleetItem(
        String targetId,
        String displayName,
        String productType,
        String environment,
        boolean enabled,
        String productVersion,
        String runtime,
        HealthStatus healthStatus,
        Integer latestAssessmentScore,
        String latestAssessmentStatus,
        Integer evidenceCompleteness,
        Integer criticalFindings,
        Integer highFindings,
        boolean metricsConfigured,
        Instant latestHealthAt,
        Instant latestAssessmentAt,
        Map<String, String> tags) {
}
