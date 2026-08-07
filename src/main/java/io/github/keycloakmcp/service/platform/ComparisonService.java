package io.github.keycloakmcp.service.platform;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Stub for comparing assessments and snapshots across time or targets.
 */
@ApplicationScoped
public class ComparisonService {

    public Map<String, Object> compareAssessments(String leftId, String rightId) {
        return Map.of(
                "status", "UNSUPPORTED",
                "message", "Assessment comparison is not implemented yet",
                "leftId", leftId == null ? "" : leftId,
                "rightId", rightId == null ? "" : rightId);
    }

    public Map<String, Object> compareSnapshots(String leftId, String rightId) {
        return Map.of(
                "status", "UNSUPPORTED",
                "message", "Prefer EnvironmentChangeService for snapshot diffs",
                "leftId", leftId == null ? "" : leftId,
                "rightId", rightId == null ? "" : rightId,
                "changes", List.of());
    }
}
