package io.github.keycloakmcp.domain.platform;

import java.time.Instant;

public record AssessmentRunSummary(
        String id,
        String targetId,
        String profile,
        int score,
        String status,
        TriggerType triggerType,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt) {
}
