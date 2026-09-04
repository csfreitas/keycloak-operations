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
        Instant completedAt) {
}
