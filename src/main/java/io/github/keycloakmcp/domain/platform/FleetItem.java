package io.github.keycloakmcp.domain.platform;

import java.time.Instant;
import java.util.Map;

/**
 * Fleet dashboard row combining target metadata with latest health/assessment signals.
 */
public record FleetItem(
        String targetId,
        String displayName,
        String productType,
        String environment,
        boolean enabled,
        HealthStatus healthStatus,
        Integer latestAssessmentScore,
        Instant latestHealthAt,
        Instant latestAssessmentAt,
        Map<String, String> tags) {
}
