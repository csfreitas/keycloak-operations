package io.github.keycloakmcp.domain.platform;

import java.time.Instant;

public record HealthCheckSummary(
        String id,
        String targetId,
        HealthStatus overallStatus,
        TriggerType triggerType,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt) {
}
