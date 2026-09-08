package io.github.keycloakmcp.domain.report;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AssessmentReport(
        String assessmentId,
        String profile,
        String status,
        int overallScore,
        int evidenceCompleteness,
        String confidence,
        Map<String, Integer> categoryScores,
        int rulesEvaluated,
        int rulesMatched,
        int rulesSkipped,
        int rulesNotEvaluated,
        List<String> missingEvidence,
        List<ReportFinding> findings,
        Instant startedAt,
        Instant completedAt,
        boolean scoreAvailable) {

    public AssessmentReport {
        // Derive even during deserialization; callers cannot certify contradictory data.
        scoreAvailable = "COMPLETE".equals(status) && evidenceCompleteness == 100 && rulesEvaluated > 0
                && rulesNotEvaluated == 0 && (missingEvidence == null || missingEvidence.isEmpty());
    }

    public AssessmentReport(String assessmentId, String profile, String status, int overallScore,
            int evidenceCompleteness, String confidence, Map<String, Integer> categoryScores,
            int rulesEvaluated, int rulesMatched, int rulesSkipped, int rulesNotEvaluated,
            List<String> missingEvidence, List<ReportFinding> findings, Instant startedAt, Instant completedAt) {
        this(assessmentId, profile, status, overallScore, evidenceCompleteness, confidence, categoryScores,
                rulesEvaluated, rulesMatched, rulesSkipped, rulesNotEvaluated, missingEvidence, findings,
                startedAt, completedAt, false);
    }
}
