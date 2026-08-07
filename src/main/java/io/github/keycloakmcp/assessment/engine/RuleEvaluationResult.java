package io.github.keycloakmcp.assessment.engine;

import java.util.List;

/**
 * Full rule-engine outcome including skipped / not-evaluated rules.
 */
public record RuleEvaluationResult(
        List<Finding> findings,
        int rulesEvaluated,
        int rulesMatched,
        int rulesSkipped,
        int rulesNotEvaluated,
        List<String> missingEvidence) {

    public static RuleEvaluationResult empty() {
        return new RuleEvaluationResult(List.of(), 0, 0, 0, 0, List.of());
    }
}
