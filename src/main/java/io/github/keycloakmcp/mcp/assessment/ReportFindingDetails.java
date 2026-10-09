package io.github.keycloakmcp.mcp.assessment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.keycloakmcp.domain.report.OperationsReport;
import io.github.keycloakmcp.domain.report.ReportFinding;

/** Bounded, detached MCP projection of the same already-sanitized report; never rule input. */
final class ReportFindingDetails {
    private static final int MAX_FINDINGS = 20;
    private static final int MAX_DEPTH = 6;
    private static final int MAX_NODES = 256;
    private static final int MAX_TEXT_CHARACTERS = 8192;
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    private ReportFindingDetails() {}

    static Map<String, Object> project(OperationsReport report) {
        var assessment = report.assessment();
        var findings = assessment == null ? null : assessment.findings();
        List<Map<String, Object>> items = new ArrayList<>();
        if (findings != null) {
            // Bound work as well as output: do not scan the rest to fill omitted slots.
            for (int index = 0; index < Math.min(MAX_FINDINGS, findings.size()); index++) {
                ReportFinding finding = findings.get(index);
                if (finding == null) continue;
                try {
                    Object copy = new Budget().copy(fields(finding), 0);
                    items.add(Map.of("sourceIndex", index, "finding", copy));
                } catch (NotRepresentable ignored) {
                    // Whole-finding omission, never a truncated string/subtree presented as complete.
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", "1.0");
        result.put("reportId", report.reportId());
        result.put("targetId", report.targetId());
        result.put("assessmentId", assessment == null ? null : assessment.assessmentId());
        result.put("availability", findings == null ? "UNAVAILABLE" : "AVAILABLE");
        result.put("totalFindings", findings == null ? null : findings.size());
        result.put("returnedFindings", items.size());
        result.put("omittedFindings", findings == null ? null : findings.size() - items.size());
        result.put("limits", Map.of("maxFindings", MAX_FINDINGS, "maxDepth", MAX_DEPTH,
                "maxNodes", MAX_NODES, "maxTextCharacters", MAX_TEXT_CHARACTERS));
        result.put("items", items);
        return result;
    }

    private static Map<String, Object> fields(ReportFinding finding) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", finding.id());
        fields.put("title", finding.title());
        fields.put("category", finding.category());
        fields.put("severity", finding.severity());
        fields.put("status", finding.status());
        fields.put("description", finding.description());
        fields.put("impact", finding.impact());
        fields.put("recommendation", finding.recommendation());
        fields.put("evidence", finding.evidence());
        fields.put("references", finding.references());
        fields.put("subjectType", finding.subjectType());
        fields.put("subjectId", finding.subjectId());
        fields.put("subjectName", finding.subjectName());
        return fields;
    }

    private static final class Budget {
        private int nodes;
        private int textCharacters;

        private void text(String text) {
            if (text.length() > MAX_TEXT_CHARACTERS - textCharacters) throw new NotRepresentable();
            textCharacters += text.length();
        }

        Object copy(Object value, int depth) {
            if (depth > MAX_DEPTH || ++nodes > MAX_NODES) throw new NotRepresentable();
            if (value == null || value instanceof Boolean) return value;
            if (value instanceof String string) {
                text(string);
                return string;
            }
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                long integer = ((Number) value).longValue();
                if (integer < -MAX_SAFE_INTEGER || integer > MAX_SAFE_INTEGER) throw new NotRepresentable();
                return value;
            }
            if (value instanceof Double || value instanceof Float) {
                double number = ((Number) value).doubleValue();
                if (!Double.isFinite(number) || Math.rint(number) == number
                        && Math.abs(number) > MAX_SAFE_INTEGER) throw new NotRepresentable();
                return value;
            }
            if (value instanceof Map<?, ?> map) {
                if (map.size() > MAX_NODES - nodes) throw new NotRepresentable();
                Map<String, Object> copy = new LinkedHashMap<>();
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) throw new NotRepresentable();
                    text(key);
                    copy.put(key, copy(entry.getValue(), depth + 1));
                }
                return copy;
            }
            if (value instanceof List<?> list) {
                if (list.size() > MAX_NODES - nodes) throw new NotRepresentable();
                List<Object> copy = new ArrayList<>(list.size());
                for (Object child : list) copy.add(copy(child, depth + 1));
                return copy;
            }
            // No arbitrary bean conversion or numeric coercion (e.g. huge BigDecimal scale).
            throw new NotRepresentable();
        }
    }

    private static final class NotRepresentable extends RuntimeException {
        private NotRepresentable() { super(null, null, false, false); }
    }
}
