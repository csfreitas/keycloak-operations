package io.github.keycloakmcp.domain.report;

import java.util.List;
import java.util.Map;

public record ReportFinding(
        String id,
        String title,
        String category,
        String severity,
        String status,
        String description,
        String impact,
        String recommendation,
        Map<String, Object> evidence,
        List<String> references,
        String subjectType,
        String subjectId,
        String subjectName) {
}
