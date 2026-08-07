package io.github.keycloakmcp.assessment.engine;

import java.util.List;
import java.util.Map;

public record Finding(
        String targetId,
        String id,
        String title,
        String category,
        Severity severity,
        FindingStatus status,
        String description,
        Map<String, Object> evidence,
        String impact,
        String recommendation,
        List<String> references) {
}
