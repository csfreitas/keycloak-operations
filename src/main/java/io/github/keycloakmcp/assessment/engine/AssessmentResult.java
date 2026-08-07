package io.github.keycloakmcp.assessment.engine;

import java.time.Instant;
import java.util.List;

/**
 * Assessment outcome. {@code id} is null until persisted by the platform layer.
 */
public record AssessmentResult(
        String id,
        String targetId,
        String profile,
        int score,
        List<Finding> findings,
        List<Evidence> evidence,
        Instant startedAt,
        Instant completedAt) {

    public AssessmentResult withId(String id) {
        return new AssessmentResult(id, targetId, profile, score, findings, evidence, startedAt, completedAt);
    }
}
