package io.github.keycloakmcp.assessment.scoring;

import java.util.List;

import io.github.keycloakmcp.assessment.engine.Finding;
import io.github.keycloakmcp.assessment.engine.FindingStatus;
import io.github.keycloakmcp.assessment.engine.Severity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AssessmentScoring {

    public int score(List<Finding> findings) {
        int score = 100;
        if (findings == null || findings.isEmpty()) {
            return score;
        }
        for (Finding finding : findings) {
            if (finding == null || finding.status() == FindingStatus.PASS) {
                continue;
            }
            score -= penalty(finding.severity());
        }
        return Math.max(0, score);
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
