package io.github.keycloakmcp.mcp.assessment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.keycloakmcp.assessment.engine.AssessmentResult;
import io.github.keycloakmcp.assessment.engine.Finding;
import io.github.keycloakmcp.assessment.engine.FindingStatus;
import io.github.keycloakmcp.assessment.profile.AssessmentProfile;
import io.github.keycloakmcp.assessment.profile.ProfileRegistry;
import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.discovery.EnvironmentDiscovery;
import io.github.keycloakmcp.discovery.EnvironmentInfo;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.platform.AssessmentRunSummary;
import io.github.keycloakmcp.domain.platform.HealthCheckSummary;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.service.platform.AssessmentHistoryService;
import io.github.keycloakmcp.service.platform.HealthCheckService;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.inject.Inject;

/**
 * Assessment and health MCP tools. Responses are compact and redacted — no full evidence dumps.
 */
public class AssessmentTools {

    private static final String TARGET_ID_HINT =
            "Identifies a previously registered Keycloak/RHBK environment. "
                    + "Use keycloak_list_targets when the target is unknown.";

    @Inject
    EnvironmentDiscovery environmentDiscovery;

    @Inject
    AssessmentHistoryService assessmentHistoryService;

    @Inject
    HealthCheckService healthCheckService;

    @Inject
    ProfileRegistry profileRegistry;

    @Inject
    TargetResolver targetResolver;

    @Inject
    TargetAuthorizationService targetAuthorization;

    @Inject
    SensitiveDataFilter sensitiveDataFilter;

    @Inject
    AuditService auditService;

    @Inject
    McpMetrics metrics;

    @Inject
    ToolAuthorization toolAuthorization;

    @Tool(
            name = "keycloak_discover_environment",
            description = "Discover runtime environment for a registered target "
                    + "(OpenShift/Kubernetes/VM/unknown) using read-only probes")
    public EnvironmentInfo keycloakDiscoverEnvironment(
            @ToolArg(description = TARGET_ID_HINT) String targetId) {
        return invoke("keycloak_discover_environment", targetId, () -> {
            var target = targetResolver.require(targetId);
            targetAuthorization.assertAllowed(target, TargetPermission.READ);
            return environmentDiscovery.discover(target);
        });
    }

    @Tool(
            name = "keycloak_run_assessment",
            description = "Run a production-readiness assessment for a target using a named profile. "
                    + "Returns a compact summary (score, status, finding counts) — not full evidence.")
    public Map<String, Object> keycloakRunAssessment(
            @ToolArg(description = TARGET_ID_HINT) String targetId,
            @ToolArg(
                    description = "Assessment profile name (e.g. keycloak-production). "
                            + "Blank uses suggestion / default.",
                    defaultValue = "")
                    String profile) {
        return invoke("keycloak_run_assessment", targetId, () -> {
            String resolved = blankToNull(profile);
            AssessmentResult result =
                    assessmentHistoryService.runAndPersist(targetId, resolved, TriggerType.MCP);
            return sensitiveDataFilter.redact(compactAssessment(result));
        });
    }

    @Tool(
            name = "keycloak_health_check",
            description = "Run a lightweight health check (Admin API reachability + infra configured). "
                    + "Distinct from full assessment.")
    public Map<String, Object> keycloakHealthCheck(
            @ToolArg(description = TARGET_ID_HINT) String targetId) {
        return invoke("keycloak_health_check", targetId, () -> {
            HealthCheckSummary summary = healthCheckService.run(targetId, TriggerType.MCP);
            return sensitiveDataFilter.redact(compactHealth(summary));
        });
    }

    @Tool(
            name = "keycloak_list_assessment_profiles",
            description = "List built-in assessment profiles with rule packs and evidence sources.")
    public List<Map<String, Object>> keycloakListAssessmentProfiles() {
        return invoke("keycloak_list_assessment_profiles", null, () -> profileRegistry.all().stream()
                .map(this::compactProfile)
                .map(sensitiveDataFilter::redact)
                .toList());
    }

    @Tool(
            name = "keycloak_list_assessments",
            description = "List assessment history for a target (compact summaries).")
    public Map<String, Object> keycloakListAssessments(
            @ToolArg(description = TARGET_ID_HINT) String targetId,
            @ToolArg(description = "Page (0-based)", defaultValue = "0") int page,
            @ToolArg(description = "Page size", defaultValue = "20") int size) {
        return invoke("keycloak_list_assessments", targetId, () -> {
            PageResult<AssessmentRunSummary> result =
                    assessmentHistoryService.list(targetId, page, size);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", result.items().stream().map(this::compactSummary).toList());
            out.put("page", result.page());
            out.put("size", result.size());
            out.put("total", result.total());
            return sensitiveDataFilter.redact(out);
        });
    }

    @Tool(
            name = "keycloak_get_assessment",
            description = "Get a persisted assessment summary by assessment id for a target.")
    public Map<String, Object> keycloakGetAssessment(
            @ToolArg(description = TARGET_ID_HINT) String targetId,
            @ToolArg(description = "Assessment run id") String assessmentId) {
        return invoke("keycloak_get_assessment", targetId, () -> sensitiveDataFilter.redact(
                compactSummary(assessmentHistoryService.get(targetId, assessmentId))));
    }

    @Tool(
            name = "keycloak_get_latest_assessment",
            description = "Get the latest assessment summary for a target, or empty map if none.")
    public Map<String, Object> keycloakGetLatestAssessment(
            @ToolArg(description = TARGET_ID_HINT) String targetId) {
        return invoke("keycloak_get_latest_assessment", targetId, () -> assessmentHistoryService
                .latest(targetId)
                .map(this::compactSummary)
                .map(sensitiveDataFilter::redact)
                .orElse(Map.of()));
    }

    @Tool(
            name = "keycloak_get_findings",
            description = "List persisted actionable findings for a target (compact; no full evidence dump).")
    public Map<String, Object> keycloakGetFindings(
            @ToolArg(description = TARGET_ID_HINT) String targetId,
            @ToolArg(description = "Optional lifecycle status filter", defaultValue = "") String lifecycleStatus,
            @ToolArg(description = "Optional severity filter", defaultValue = "") String severity,
            @ToolArg(description = "Page (0-based)", defaultValue = "0") int page,
            @ToolArg(description = "Page size", defaultValue = "20") int size) {
        return invoke("keycloak_get_findings", targetId, () -> {
            PageResult<Finding> result = assessmentHistoryService.findings(
                    targetId,
                    blankToOptional(lifecycleStatus),
                    blankToOptional(severity),
                    page,
                    size);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put(
                    "items",
                    result.items().stream().map(this::compactFinding).toList());
            out.put("page", result.page());
            out.put("size", result.size());
            out.put("total", result.total());
            return sensitiveDataFilter.redact(out);
        });
    }

    private Map<String, Object> compactAssessment(AssessmentResult result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", result.id());
        out.put("targetId", result.targetId());
        out.put("profile", result.profile());
        out.put("status", result.status() == null ? null : result.status().name());
        out.put("overallScore", result.overallScore());
        out.put("evidenceCompleteness", result.evidenceCompleteness());
        out.put("confidence", result.confidence() == null ? null : result.confidence().name());
        out.put("categoryScores", result.categoryScores());
        out.put("rulesEvaluated", result.rulesEvaluated());
        out.put("rulesMatched", result.rulesMatched());
        out.put("rulesSkipped", result.rulesSkipped());
        out.put("rulesNotEvaluated", result.rulesNotEvaluated());
        out.put("missingEvidence", result.missingEvidence());
        List<Finding> actionable = result.findings() == null
                ? List.of()
                : result.findings().stream()
                        .filter(f -> f.status() == FindingStatus.OPEN
                                || f.status() == FindingStatus.FAIL
                                || f.status() == FindingStatus.WARNING)
                        .toList();
        out.put("findingCount", actionable.size());
        out.put(
                "findings",
                actionable.stream().map(this::compactFinding).collect(Collectors.toList()));
        out.put("startedAt", result.startedAt());
        out.put("completedAt", result.completedAt());
        return out;
    }

    private Map<String, Object> compactFinding(Finding finding) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", finding.id());
        out.put("title", finding.title());
        out.put("category", finding.category());
        out.put("severity", finding.severity() == null ? null : finding.severity().name());
        out.put("status", finding.status() == null ? null : finding.status().name());
        out.put("recommendation", finding.recommendation());
        if (finding.subject() != null) {
            out.put(
                    "subject",
                    Map.of(
                            "type",
                            finding.subject().type() == null ? "" : finding.subject().type().name(),
                            "id",
                            finding.subject().id() == null ? "" : finding.subject().id()));
        }
        // Evidence keys only — not full values dump
        if (finding.evidence() != null) {
            out.put("evidenceKeys", List.copyOf(finding.evidence().keySet()));
        }
        return out;
    }

    private Map<String, Object> compactSummary(AssessmentRunSummary summary) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", summary.id());
        out.put("targetId", summary.targetId());
        out.put("profile", summary.profile());
        out.put("score", summary.score());
        out.put("status", summary.status());
        out.put("triggerType", summary.triggerType() == null ? null : summary.triggerType().name());
        out.put("evidenceCompleteness", summary.evidenceCompleteness());
        out.put("confidence", summary.confidence());
        out.put("categoryScores", summary.categoryScores());
        out.put("findingCounts", summary.findingCounts());
        out.put("startedAt", summary.startedAt());
        out.put("completedAt", summary.completedAt());
        out.put("createdAt", summary.createdAt());
        return out;
    }

    private Map<String, Object> compactHealth(HealthCheckSummary summary) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", summary.id());
        out.put("targetId", summary.targetId());
        out.put("overallStatus", summary.overallStatus() == null ? null : summary.overallStatus().name());
        out.put("triggerType", summary.triggerType() == null ? null : summary.triggerType().name());
        out.put("startedAt", summary.startedAt());
        out.put("completedAt", summary.completedAt());
        return out;
    }

    private Map<String, Object> compactProfile(AssessmentProfile profile) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", profile.name());
        out.put("description", profile.description());
        out.put("products", profile.products());
        out.put("runtimes", profile.runtimes());
        out.put("rulePackIds", profile.rulePackIds());
        out.put("requiredEvidenceSources", profile.requiredEvidenceSources());
        out.put("optionalEvidenceSources", profile.optionalEvidenceSources());
        return out;
    }

    private <T> T invoke(String toolName, String targetId, ThrowingSupplier<T> action) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            toolAuthorization.assertReadOnlyOperation(toolName);
            T result = action.get();
            success = true;
            return result;
        } catch (McpException e) {
            throw new ToolCallException(e.getError().code() + ": " + e.getMessage());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolCallException(e.getMessage());
        } finally {
            long duration = System.currentTimeMillis() - start;
            metrics.recordToolInvocation(toolName, duration, success);
            auditService.logToolInvocation(toolName, targetId, null, duration, success);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static java.util.Optional<String> blankToOptional(String value) {
        return value == null || value.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(value);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
