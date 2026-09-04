package io.github.keycloakmcp.mcp.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.domain.report.OperationsReport;
import io.github.keycloakmcp.domain.report.ReportSection;
import io.github.keycloakmcp.domain.report.ReportSectionStatus;
import io.github.keycloakmcp.domain.report.ReportStatus;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.service.platform.OperationsReportService;

class AssessmentToolsOperationsReportTest {

    @Test
    void delegatesToSharedServiceAndReturnsCompactReport() {
        OperationsReportService service = mock(OperationsReportService.class);
        OperationsReport report = new OperationsReport(
                "1.0",
                "report-1",
                "rhbk-prd",
                "RHBK Production",
                "RHBK",
                "PRD",
                "OPENSHIFT",
                Instant.parse("2026-09-04T10:00:00Z"),
                ReportStatus.COMPLETE,
                List.of(new ReportSection("health", ReportSectionStatus.COMPLETE, "done")),
                null,
                null,
                null,
                null,
                "# Safe report");
        when(service.generate("rhbk-prd", "keycloak-production", "15m", TriggerType.MCP)).thenReturn(report);
        AssessmentTools tools = new AssessmentTools();
        tools.operationsReportService = service;
        tools.sensitiveDataFilter = new SensitiveDataFilter(new ObjectMapper().findAndRegisterModules());
        tools.auditService = mock(AuditService.class);
        tools.metrics = mock(McpMetrics.class);
        tools.toolAuthorization = mock(ToolAuthorization.class);

        Map<String, Object> result =
                tools.keycloakGenerateOperationsReport("rhbk-prd", "keycloak-production", "15m");

        assertThat(result)
                .containsEntry("reportId", "report-1")
                .containsEntry("targetId", "rhbk-prd")
                .containsEntry("markdown", "# Safe report");
        verify(tools.toolAuthorization).assertReadOnlyOperation("keycloak_generate_operations_report");
        verify(service).generate("rhbk-prd", "keycloak-production", "15m", TriggerType.MCP);
    }
}
