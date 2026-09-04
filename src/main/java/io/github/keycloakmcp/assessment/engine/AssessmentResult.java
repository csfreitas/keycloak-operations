package io.github.keycloakmcp.assessment.engine;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Assessment outcome. {@code id} is null until persisted by the platform layer.
 */
public record AssessmentResult(
        String id,
        String targetId,
        String profile,
        AssessmentScope scope,
        AssessmentStatus status,
        int overallScore,
        Map<String, Integer> categoryScores,
        int evidenceCompleteness,
        AssessmentConfidence confidence,
        int rulesEvaluated,
        int rulesMatched,
        int rulesSkipped,
        int rulesNotEvaluated,
        List<String> missingEvidence,
        List<Finding> findings,
        List<Evidence> evidence,
        Instant startedAt,
        Instant completedAt) {

    /** Backward-compatible accessor used by older call sites. */
    public int score() {
        return overallScore;
    }

    /** The legacy numeric score is not a posture conclusion when evidence is incomplete. */
    @JsonProperty(value = "scoreAvailable", access = JsonProperty.Access.READ_ONLY)
    public boolean scoreAvailable() {
        return status == AssessmentStatus.COMPLETE && evidenceCompleteness == 100 && rulesEvaluated > 0
                && rulesNotEvaluated == 0 && (missingEvidence == null || missingEvidence.isEmpty());
    }

    public AssessmentResult withId(String id) {
        return new AssessmentResult(
                id,
                targetId,
                profile,
                scope,
                status,
                overallScore,
                categoryScores,
                evidenceCompleteness,
                confidence,
                rulesEvaluated,
                rulesMatched,
                rulesSkipped,
                rulesNotEvaluated,
                missingEvidence,
                findings,
                evidence,
                startedAt,
                completedAt);
    }
}
