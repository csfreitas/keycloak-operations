package io.github.keycloakmcp.domain.platform;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Health check run with component results. */
public record HealthCheckDetail(
        String id,
        String targetId,
        HealthStatus overallStatus,
        TriggerType triggerType,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        List<HealthComponentView> components,
        Map<String, Object> summary) {
}
