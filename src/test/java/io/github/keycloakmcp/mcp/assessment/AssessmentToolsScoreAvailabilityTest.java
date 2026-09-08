package io.github.keycloakmcp.mcp.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.assessment.engine.AssessmentConfidence;
import io.github.keycloakmcp.assessment.engine.AssessmentResult;
import io.github.keycloakmcp.assessment.engine.AssessmentScope;
import io.github.keycloakmcp.assessment.engine.AssessmentStatus;
import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.platform.AssessmentRunSummary;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.service.platform.AssessmentHistoryService;

class AssessmentToolsScoreAvailabilityTest {
    private AssessmentTools tools;
    private AssessmentHistoryService history;

    @BeforeEach
    void setup() {
        history = mock(AssessmentHistoryService.class);
        tools = new AssessmentTools();
        tools.assessmentHistoryService = history;
        tools.sensitiveDataFilter = new SensitiveDataFilter(new ObjectMapper().findAndRegisterModules());
        tools.auditService = mock(AuditService.class);
        tools.metrics = mock(McpMetrics.class);
        tools.toolAuthorization = mock(ToolAuthorization.class);
    }

    @Test
    void historicalHundredWithoutProvenanceHasNoMcpScore() {
        Instant now = Instant.now();
        when(history.get("target-a", "legacy")).thenReturn(new AssessmentRunSummary(
                "legacy", "target-a", "profile", 100, "COMPLETE", TriggerType.API,
                now, now, now, 100, "HIGH", Map.of("security", 100), Map.of()));
        Map<String, Object> result = tools.keycloakGetAssessment("target-a", "legacy");
        assertThat(result.get("score")).isNull();
        assertThat(result.get("scoreAvailable")).isNull();
        assertThat(result.get("categoryScores")).isEqualTo(Map.of());
    }

    @Test
    void trustedCompleteHistoricalScoreRemainsAvailable() {
        Instant now = Instant.now();
        when(history.get("target-a", "current")).thenReturn(new AssessmentRunSummary(
                "current", "target-a", "profile", 100, "COMPLETE", TriggerType.API,
                now, now, now, 100, "HIGH", Map.of("security", 100), Map.of(), true, "known-revision"));
        assertThat(tools.keycloakGetAssessment("target-a", "current"))
                .containsEntry("score", 100).containsEntry("scoreAvailable", true);
    }

    @Test
    void partialLiveAssessmentHasNeitherOverallNorCategoryScores() {
        Instant now = Instant.now();
        when(history.runAndPersist("target-a", "profile", TriggerType.MCP)).thenReturn(new AssessmentResult(
                "run", "target-a", "profile", AssessmentScope.target("target-a"),
                AssessmentStatus.PARTIAL, 100, Map.of("security", 100), 99, AssessmentConfidence.LOW,
                1, 0, 0, 1, List.of("missing"), List.of(), List.of(), now, now));
        Map<String, Object> result = tools.keycloakRunAssessment("target-a", "profile");
        assertThat(result.get("overallScore")).isNull();
        assertThat(result.get("scoreAvailable")).isEqualTo(false);
        assertThat(result.get("categoryScores")).isEqualTo(Map.of());
    }

    @Test
    void contradictoryCompleteResultHasNoMcpScore() {
        Instant now = Instant.now();
        for (AssessmentResult assessment : List.of(
                new AssessmentResult("counts", "target-a", "profile", AssessmentScope.target("target-a"),
                        AssessmentStatus.COMPLETE, 100, Map.of("security", 100), 100, AssessmentConfidence.HIGH,
                        2, 0, 0, 1, List.of(), List.of(), List.of(), now, now),
                new AssessmentResult("missing", "target-a", "profile", AssessmentScope.target("target-a"),
                        AssessmentStatus.COMPLETE, 100, Map.of("security", 100), 100, AssessmentConfidence.HIGH,
                        2, 0, 0, 0, List.of("unavailable-source"), List.of(), List.of(), now, now))) {
            when(history.runAndPersist("target-a", "profile", TriggerType.MCP)).thenReturn(assessment);
            Map<String, Object> result = tools.keycloakRunAssessment("target-a", "profile");
            assertThat(result.get("overallScore")).isNull();
            assertThat(result.get("scoreAvailable")).isEqualTo(false);
            assertThat(result.get("categoryScores")).isEqualTo(Map.of());
        }
    }
}
