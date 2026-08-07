package io.github.keycloakmcp.domain.platform;

import java.time.Instant;
import java.util.Map;

/**
 * Compact assessment run summary for REST/MCP. Newer fields are nullable for
 * backward compatibility with rows persisted before V6.
 */
public record AssessmentRunSummary(
        String id,
        String targetId,
        String profile,
        int score,
        String status,
        TriggerType triggerType,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Integer evidenceCompleteness,
        String confidence,
        Map<String, Integer> categoryScores,
        Map<String, Integer> findingCounts) {

    /** Backward-compatible constructor. */
    public AssessmentRunSummary(
            String id,
            String targetId,
            String profile,
            int score,
            String status,
            TriggerType triggerType,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt) {
        this(id, targetId, profile, score, status, triggerType, startedAt, completedAt, createdAt,
                null, null, null, null);
    }
}
