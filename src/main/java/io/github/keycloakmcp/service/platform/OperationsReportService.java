package io.github.keycloakmcp.service.platform;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.assessment.engine.AssessmentResult;
import io.github.keycloakmcp.assessment.engine.AssessmentStatus;
import io.github.keycloakmcp.assessment.engine.Finding;
import io.github.keycloakmcp.assessment.engine.FindingStatus;
import io.github.keycloakmcp.domain.metrics.PerformanceSummary;
import io.github.keycloakmcp.domain.platform.HealthCheckDetail;
import io.github.keycloakmcp.domain.platform.HealthCheckSummary;
import io.github.keycloakmcp.domain.platform.SnapshotDetail;
import io.github.keycloakmcp.domain.platform.SnapshotSummary;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.domain.report.AssessmentReport;
import io.github.keycloakmcp.domain.report.OperationsReport;
import io.github.keycloakmcp.domain.report.ReportFinding;
import io.github.keycloakmcp.domain.report.ReportSection;
import io.github.keycloakmcp.domain.report.ReportSectionStatus;
import io.github.keycloakmcp.domain.report.ReportStatus;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.observability.metrics.MetricsProviderStatus;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Builds a single human-readable report from the platform's existing deterministic services. */
@ApplicationScoped
public class OperationsReportService {

    public static final String SCHEMA_VERSION = "1.0";

    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final SnapshotService snapshotService;
    private final HealthCheckService healthCheckService;
    private final AssessmentHistoryService assessmentHistoryService;
    private final MetricsService metricsService;
    private final SensitiveDataFilter sensitiveDataFilter;
    private final ObjectMapper objectMapper;

    @Inject
    public OperationsReportService(
            TargetResolver targetResolver,
            TargetAuthorizationService targetAuthorization,
            SnapshotService snapshotService,
            HealthCheckService healthCheckService,
            AssessmentHistoryService assessmentHistoryService,
            MetricsService metricsService,
            SensitiveDataFilter sensitiveDataFilter,
            ObjectMapper objectMapper) {
        this.targetResolver = targetResolver;
        this.targetAuthorization = targetAuthorization;
        this.snapshotService = snapshotService;
        this.healthCheckService = healthCheckService;
        this.assessmentHistoryService = assessmentHistoryService;
        this.metricsService = metricsService;
        this.sensitiveDataFilter = sensitiveDataFilter;
        this.objectMapper = objectMapper;
    }

    public OperationsReport generate(
            String targetId,
            String profile,
            String metricsWindow,
            TriggerType triggerType) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.ASSESS);
        TriggerType trigger = triggerType == null ? TriggerType.API : triggerType;
        Instant generatedAt = Instant.now();
        List<ReportSection> sections = new ArrayList<>();

        SnapshotDetail snapshot = collectSnapshot(targetId, sections);
        HealthCheckDetail health = collectHealth(targetId, trigger, sections);
        AssessmentReport assessment = collectAssessment(targetId, profile, trigger, sections);
        PerformanceSummary performance = collectPerformance(target, metricsWindow, sections);
        ReportStatus status = reportStatus(sections);

        OperationsReport draft = new OperationsReport(
                SCHEMA_VERSION,
                UUID.randomUUID().toString(),
                target.id().value(),
                target.displayName(),
                target.type().name(),
                target.environment().name(),
                target.infrastructureTypeOrNone().name(),
                generatedAt,
                status,
                List.copyOf(sections),
                snapshot,
                health,
                assessment,
                performance,
                null);
        String markdown = sensitiveDataFilter.redactString(renderMarkdown(draft));
        return new OperationsReport(
                draft.schemaVersion(),
                draft.reportId(),
                draft.targetId(),
                draft.targetDisplayName(),
                draft.productType(),
                draft.environment(),
                draft.configuredInfrastructureType(),
                draft.generatedAt(),
                draft.status(),
                draft.sections(),
                draft.environmentSnapshot(),
                draft.healthCheck(),
                draft.assessment(),
                draft.performance(),
                markdown);
    }

    private SnapshotDetail collectSnapshot(String targetId, List<ReportSection> sections) {
        try {
            SnapshotSummary summary = snapshotService.create(targetId);
            SnapshotDetail detail = snapshotService.getDetail(targetId, summary.id());
            boolean partial = containsKey(detail.summary(), "collectionError")
                    || containsNonEmptyCollection(detail.summary(), "warnings");
            sections.add(partial
                    ? partial("platform", "Environment snapshot collected with incomplete infrastructure evidence")
                    : complete("platform", "Environment and infrastructure snapshot collected"));
            return safeSnapshot(detail);
        } catch (RuntimeException e) {
            sections.add(failed("platform", e));
            return null;
        }
    }

    private HealthCheckDetail collectHealth(
            String targetId,
            TriggerType trigger,
            List<ReportSection> sections) {
        try {
            HealthCheckSummary summary = healthCheckService.run(targetId, trigger);
            HealthCheckDetail detail = healthCheckService.get(targetId, summary.id());
            sections.add(complete("health", "Health checks completed"));
            return sensitiveDataFilter.redact(detail);
        } catch (RuntimeException e) {
            sections.add(failed("health", e));
            return null;
        }
    }

    private AssessmentReport collectAssessment(
            String targetId,
            String profile,
            TriggerType trigger,
            List<ReportSection> sections) {
        try {
            AssessmentResult result = assessmentHistoryService.runAndPersist(targetId, blankToNull(profile), trigger);
            AssessmentReport report = toAssessmentReport(result);
            AssessmentStatus assessmentStatus = result.status();
            if (assessmentStatus == AssessmentStatus.COMPLETE) {
                sections.add(complete("assessment", "Deterministic assessment completed"));
            } else if (assessmentStatus == AssessmentStatus.FAILED) {
                sections.add(failed("assessment", "Assessment could not be performed meaningfully"));
            } else {
                sections.add(partial("assessment", assessmentStatus == AssessmentStatus.PARTIAL
                        ? "Assessment completed with incomplete evidence"
                        : "Assessment completed without an execution status"));
            }
            return report;
        } catch (RuntimeException e) {
            sections.add(failed("assessment", e));
            return null;
        }
    }

    private PerformanceSummary collectPerformance(
            Target target,
            String metricsWindow,
            List<ReportSection> sections) {
        if (!target.hasMetrics()) {
            sections.add(new ReportSection(
                    "performance",
                    ReportSectionStatus.SKIPPED,
                    "Metrics provider is not configured for this target"));
            return null;
        }
        try {
            PerformanceSummary summary = metricsService.summary(target.id().value(), metricsWindow);
            MetricsProviderStatus providerStatus = summary.providerStatus();
            sections.add(providerStatus == MetricsProviderStatus.AVAILABLE
                    ? complete("performance", "Semantic performance summary collected")
                    : partial("performance", "Performance provider status: "
                            + (providerStatus == null ? MetricsProviderStatus.UNKNOWN : providerStatus)));
            return sensitiveDataFilter.redact(summary);
        } catch (RuntimeException e) {
            sections.add(failed("performance", e));
            return null;
        }
    }

    private AssessmentReport toAssessmentReport(AssessmentResult result) {
        List<ReportFinding> findings = result.findings() == null
                ? List.of()
                : result.findings().stream()
                        .filter(OperationsReportService::isActionable)
                        .map(this::toReportFinding)
                        .toList();
        return new AssessmentReport(
                result.id(),
                result.profile(),
                result.status() == null ? null : result.status().name(),
                result.overallScore(),
                result.evidenceCompleteness(),
                result.confidence() == null ? null : result.confidence().name(),
                result.categoryScores() == null ? Map.of() : Map.copyOf(result.categoryScores()),
                result.rulesEvaluated(),
                result.rulesMatched(),
                result.rulesSkipped(),
                result.rulesNotEvaluated(),
                result.missingEvidence() == null ? List.of() : List.copyOf(result.missingEvidence()),
                findings,
                result.startedAt(),
                result.completedAt());
    }

    private ReportFinding toReportFinding(Finding finding) {
        Map<String, Object> evidence = finding.evidence() == null
                ? Map.of()
                : sensitiveDataFilter.redact(new LinkedHashMap<>(finding.evidence()));
        return new ReportFinding(
                finding.id(),
                finding.title(),
                finding.category(),
                finding.severity() == null ? null : finding.severity().name(),
                finding.status() == null ? null : finding.status().name(),
                finding.description(),
                finding.impact(),
                finding.recommendation(),
                evidence,
                finding.references() == null ? List.of() : List.copyOf(finding.references()),
                finding.subject() == null || finding.subject().type() == null
                        ? null
                        : finding.subject().type().name(),
                finding.subject() == null ? null : finding.subject().id(),
                finding.subject() == null ? null : finding.subject().displayName());
    }

    private String renderMarkdown(OperationsReport report) {
        StringBuilder out = new StringBuilder();
        out.append("# Keycloak / RHBK Operations Report\n\n");
        out.append("- Target: `").append(markdown(report.targetId())).append("` — ")
                .append(markdown(report.targetDisplayName())).append('\n');
        out.append("- Product: ").append(markdown(report.productType())).append('\n');
        out.append("- Environment: ").append(markdown(report.environment())).append('\n');
        out.append("- Infrastructure: ").append(markdown(report.configuredInfrastructureType())).append('\n');
        out.append("- Generated at: ").append(report.generatedAt()).append('\n');
        out.append("- Report completeness: **").append(report.status()).append("**\n\n");

        out.append("## Collection status\n\n");
        out.append("| Section | Status | Message |\n|---|---|---|\n");
        for (ReportSection section : report.sections()) {
            out.append("| ").append(markdown(section.name()))
                    .append(" | ").append(section.status())
                    .append(" | ").append(markdown(section.message())).append(" |\n");
        }

        out.append("\n## Health\n\n");
        if (report.healthCheck() == null) {
            out.append("Health data was not available.\n");
        } else {
            out.append("Overall status: **").append(report.healthCheck().overallStatus()).append("**\n\n");
            out.append("| Component | Status | Message | Duration (ms) |\n|---|---|---|---:|\n");
            report.healthCheck().components().forEach(component -> out.append("| ")
                    .append(markdown(component.name())).append(" | ")
                    .append(component.status()).append(" | ")
                    .append(markdown(component.message())).append(" | ")
                    .append(component.durationMs() == null ? "" : component.durationMs()).append(" |\n"));
        }

        out.append("\n## Assessment\n\n");
        if (report.assessment() == null) {
            out.append("Assessment data was not available.\n");
        } else {
            AssessmentReport assessment = report.assessment();
            out.append("- Profile: `").append(markdown(assessment.profile())).append("`\n");
            out.append("- Status: **").append(assessment.status()).append("**\n");
            out.append("- Score: **").append(assessment.overallScore()).append("**\n");
            out.append("- Evidence completeness: **").append(assessment.evidenceCompleteness()).append("%**\n");
            out.append("- Confidence: **").append(markdown(assessment.confidence())).append("**\n");
            out.append("- Actionable findings: **").append(assessment.findings().size()).append("**\n\n");
            for (ReportFinding finding : assessment.findings()) {
                out.append("### ").append(markdown(finding.id())).append(" — ")
                        .append(markdown(finding.title())).append("\n\n");
                out.append("Severity: **").append(markdown(finding.severity())).append("**  \n");
                out.append(markdown(finding.description())).append("\n\n");
                if (finding.recommendation() != null && !finding.recommendation().isBlank()) {
                    out.append("Recommendation: ").append(markdown(finding.recommendation())).append("\n\n");
                }
            }
        }

        out.append("## Platform and configuration evidence\n\n");
        if (report.environmentSnapshot() == null) {
            out.append("Platform inventory was not available.\n");
        } else {
            out.append("```json\n")
                    .append(prettyJson(compactPlatformEvidence(report.environmentSnapshot().summary())))
                    .append("\n```\n");
        }

        out.append("\n## Performance\n\n");
        if (report.performance() == null) {
            out.append("Performance metrics were not available or not configured.\n");
        } else {
            out.append("```json\n").append(prettyJson(report.performance())).append("\n```\n");
        }
        out.append("\n---\nAssessment outcomes are produced from collected evidence and deterministic rules. ")
                .append("AI may explain this report but does not decide health, findings, scores, risk, or policy.\n");
        return out.toString();
    }

    private String prettyJson(Object value) {
        try {
            Object sanitized = sensitiveDataFilter.redact(value);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(sanitized);
        } catch (JsonProcessingException e) {
            return "{\"serializationStatus\":\"FAILED\"}";
        }
    }

    private ReportSection failed(String section, RuntimeException error) {
        return new ReportSection(
                section,
                ReportSectionStatus.FAILED,
                "Collection failed (" + error.getClass().getSimpleName() + ")");
    }

    private static ReportSection failed(String section, String message) {
        return new ReportSection(section, ReportSectionStatus.FAILED, message);
    }

    private static ReportSection complete(String section, String message) {
        return new ReportSection(section, ReportSectionStatus.COMPLETE, message);
    }

    private static ReportSection partial(String section, String message) {
        return new ReportSection(section, ReportSectionStatus.PARTIAL, message);
    }

    private static ReportStatus reportStatus(List<ReportSection> sections) {
        long usableCoreSections = sections.stream()
                .filter(section -> isCore(section.name()))
                .filter(section -> section.status() == ReportSectionStatus.COMPLETE
                        || section.status() == ReportSectionStatus.PARTIAL)
                .count();
        boolean incomplete = sections.stream().anyMatch(section -> section.status() == ReportSectionStatus.FAILED
                || section.status() == ReportSectionStatus.PARTIAL);
        if (usableCoreSections == 0) {
            return ReportStatus.FAILED;
        }
        return incomplete ? ReportStatus.PARTIAL : ReportStatus.COMPLETE;
    }

    private static boolean isCore(String section) {
        return "platform".equals(section) || "health".equals(section) || "assessment".equals(section);
    }

    private static boolean isActionable(Finding finding) {
        return finding != null
                && (finding.status() == FindingStatus.OPEN
                        || finding.status() == FindingStatus.FAIL
                        || finding.status() == FindingStatus.WARNING);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String markdown(String value) {
        return value == null ? "" : value.replace("`", "'").replace("|", "\\|").replace('\n', ' ').replace('\r', ' ');
    }

    private SnapshotDetail safeSnapshot(SnapshotDetail detail) {
        Map<String, Object> safeSummary = sanitizeReportMap(detail.summary());
        return new SnapshotDetail(
                detail.id(),
                detail.targetId(),
                detail.snapshotHash(),
                detail.createdAt(),
                sensitiveDataFilter.redact(safeSummary));
    }

    private static Map<String, Object> compactPlatformEvidence(Map<String, Object> summary) {
        if (summary == null) {
            return Map.of();
        }
        Map<String, Object> compact = new LinkedHashMap<>();
        for (String key : List.of(
                "targetId",
                "displayName",
                "productType",
                "environment",
                "infraType",
                "serverProduct",
                "serverVersion",
                "configurationHash",
                "runtimeStateHash")) {
            if (summary.containsKey(key)) {
                compact.put(key, summary.get(key));
            }
        }
        Object inventoryValue = summary.get("inventory");
        if (inventoryValue instanceof Map<?, ?> inventory) {
            Map<String, Object> compactInventory = new LinkedHashMap<>();
            for (String key : List.of(
                    "runtime", "cluster", "keycloak", "topology", "scheduling", "hpa", "pdb", "resources",
                    "networking", "warnings", "collectedAt", "collectionError")) {
                if (inventory.containsKey(key)) {
                    compactInventory.put(key, inventory.get(key));
                }
            }
            compact.put("inventory", compactInventory);
        }
        return compact;
    }

    private Map<String, Object> sanitizeReportMap(Map<String, ?> input) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (input == null) {
            return safe;
        }
        for (Map.Entry<String, ?> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || isEndpointKey(key)) {
                continue;
            }
            if (key.endsWith("Error")) {
                safe.put(key, "COLLECTION_FAILED");
            } else if (value instanceof Map<?, ?> nested) {
                Map<String, Object> nestedStrings = new LinkedHashMap<>();
                nested.forEach((nestedKey, nestedValue) -> nestedStrings.put(String.valueOf(nestedKey), nestedValue));
                safe.put(key, sanitizeReportMap(nestedStrings));
            } else if (value instanceof List<?> list) {
                safe.put(key, list.stream().map(this::sanitizeReportValue).toList());
            } else {
                safe.put(key, value);
            }
        }
        return safe;
    }

    private Object sanitizeReportValue(Object value) {
        if (value instanceof Map<?, ?> nested) {
            Map<String, Object> nestedStrings = new LinkedHashMap<>();
            nested.forEach((key, nestedValue) -> nestedStrings.put(String.valueOf(key), nestedValue));
            return sanitizeReportMap(nestedStrings);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::sanitizeReportValue).toList();
        }
        return value;
    }

    private static boolean isEndpointKey(String key) {
        String normalized = key.replace("-", "").replace("_", "").toLowerCase();
        return normalized.equals("keycloakurl")
                || normalized.equals("managementurl")
                || normalized.equals("endpointurl")
                || normalized.equals("credentialref");
    }

    private static boolean containsKey(Object value, String key) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().anyMatch(entry -> key.equals(entry.getKey())
                    || containsKey(entry.getValue(), key));
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (containsKey(item, key)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsNonEmptyCollection(Object value, String key) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (key.equals(entry.getKey()) && entry.getValue() instanceof java.util.Collection<?> collection) {
                    return !collection.isEmpty();
                }
                if (containsNonEmptyCollection(entry.getValue(), key)) {
                    return true;
                }
            }
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (containsNonEmptyCollection(item, key)) {
                    return true;
                }
            }
        }
        return false;
    }
}
