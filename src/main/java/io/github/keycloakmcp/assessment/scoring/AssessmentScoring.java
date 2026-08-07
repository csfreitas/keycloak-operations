package io.github.keycloakmcp.assessment.scoring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.keycloakmcp.assessment.engine.Finding;
import io.github.keycloakmcp.assessment.engine.FindingStatus;
import io.github.keycloakmcp.assessment.engine.Severity;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Deterministic scoring: start at 100, subtract severity penalties for OPEN/FAIL findings only.
 * NOT_EVALUATED / PASS / SKIPPED do not reduce score.
 * Category scores use the same algorithm scoped to findings in that category.
 */
@ApplicationScoped
public class AssessmentScoring {

    public static final List<String> CATEGORIES = List.of(
            "availability",
            "security",
            "configuration",
            "operations",
            "observability",
            "capacity");

    public int score(List<Finding> findings) {
        return scoreFor(findings, null);
    }

    public Map<String, Integer> categoryScores(List<Finding> findings) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            scores.put(category, scoreFor(findings, category));
        }
        // Also include any non-standard categories that have OPEN findings
        if (findings != null) {
            for (Finding finding : findings) {
                if (finding == null || finding.category() == null) {
                    continue;
                }
                String normalized = normalizeCategory(finding.category());
                scores.putIfAbsent(normalized, scoreFor(findings, normalized));
            }
        }
        return Map.copyOf(scores);
    }

    private int scoreFor(List<Finding> findings, String categoryFilter) {
        int score = 100;
        if (findings == null || findings.isEmpty()) {
            return score;
        }
        for (Finding finding : findings) {
            if (finding == null) {
                continue;
            }
            if (finding.status() == FindingStatus.PASS
                    || finding.status() == FindingStatus.NOT_EVALUATED
                    || finding.status() == FindingStatus.SKIPPED) {
                continue;
            }
            if (categoryFilter != null) {
                String cat = normalizeCategory(finding.category());
                if (!categoryFilter.equals(cat)) {
                    continue;
                }
            }
            score -= penalty(finding.severity());
        }
        return Math.max(0, score);
    }

    /**
     * Maps rule categories onto scoring buckets.
     */
    public static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "operations";
        }
        String c = category.trim().toLowerCase(Locale.ROOT);
        return switch (c) {
            case "high-availability", "ha", "availability" -> "availability";
            case "security", "security-baseline" -> "security";
            case "production", "configuration", "config" -> "configuration";
            case "operations", "ops" -> "operations";
            case "observability", "metrics", "tracing" -> "observability";
            case "capacity", "resources" -> "capacity";
            default -> c;
        };
    }

    private static int penalty(Severity severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity) {
            case CRITICAL -> 25;
            case HIGH -> 15;
            case MEDIUM -> 8;
            case LOW -> 3;
            case INFO -> 0;
        };
    }
}
