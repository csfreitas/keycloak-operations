package io.github.keycloakmcp.domain.report;

public record ReportSection(
        String name,
        ReportSectionStatus status,
        String message) {
}
