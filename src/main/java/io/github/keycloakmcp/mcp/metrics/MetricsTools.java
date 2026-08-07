package io.github.keycloakmcp.mcp.metrics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.metrics.MetricsStatusView;
import io.github.keycloakmcp.domain.metrics.PerformanceSummary;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.observability.metrics.MetricCategory;
import io.github.keycloakmcp.observability.metrics.SemanticMetricResult;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.service.platform.MetricsService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.inject.Inject;

/**
 * Semantic metrics MCP tools. Never accept PromQL; responses are compact and redacted.
 */
public class MetricsTools {

    private static final String TARGET_ID_HINT =
            "Identifies a previously registered Keycloak/RHBK environment. "
                    + "Use keycloak_list_targets when the target is unknown.";

    @Inject
    MetricsService metricsService;

    @Inject
    SensitiveDataFilter sensitiveDataFilter;

    @Inject
    AuditService auditService;

    @Inject
    McpMetrics metrics;

    @Inject
    ToolAuthorization toolAuthorization;

    @Tool(
            name = "keycloak_get_metrics_status",
            description = "Get metrics provider status for a target (configured/available). No PromQL.")
    public Map<String, Object> keycloakGetMetricsStatus(
            @ToolArg(description = TARGET_ID_HINT) String targetId) {
        return invoke("keycloak_get_metrics_status", targetId, () -> {
            MetricsStatusView status = metricsService.status(targetId);
            Map<String, Object> compact = new LinkedHashMap<>();
            compact.put("targetId", status.targetId());
            compact.put("status", status.status().name());
            compact.put("metricsType", status.metricsType());
            compact.put("configured", status.configured());
            compact.put("message", status.message());
            return sensitiveDataFilter.redact(compact);
        });
    }

    @Tool(
            name = "keycloak_get_performance_summary",
            description = "Get a compact performance summary (HTTP/DB/JVM/cache/cluster). "
                    + "Optional window: 1m,5m,15m,30m,1h,6h,24h. Never accepts PromQL.")
    public Map<String, Object> keycloakGetPerformanceSummary(
            @ToolArg(description = TARGET_ID_HINT) String targetId,
            @ToolArg(description = "Metric window (default 5m)", defaultValue = "") String window) {
        return invoke("keycloak_get_performance_summary", targetId, () -> {
            PerformanceSummary summary = metricsService.summary(targetId, blankToNull(window));
            return sensitiveDataFilter.redact(compactSummary(summary));
        });
    }

    @Tool(
            name = "keycloak_get_metrics",
            description = "Get semantic metrics for a category (HTTP, DATABASE, JVM, CACHE, "
                    + "AUTHENTICATION, CLUSTER, RUNTIME). Optional window. Never accepts PromQL.")
    public List<Map<String, Object>> keycloakGetMetrics(
            @ToolArg(description = TARGET_ID_HINT) String targetId,
            @ToolArg(description = "Metric category enum name") String category,
            @ToolArg(description = "Metric window (default 5m)", defaultValue = "") String window) {
        return invoke("keycloak_get_metrics", targetId, () -> {
            MetricCategory cat = parseCategory(category);
            List<SemanticMetricResult> results =
                    metricsService.category(targetId, cat, blankToNull(window));
            return sensitiveDataFilter.redact(results.stream().map(MetricsTools::compactResult).toList());
        });
    }

    private <T> T invoke(String toolName, String targetId, java.util.concurrent.Callable<T> action) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            toolAuthorization.assertReadOnlyOperation(toolName);
            T result = action.call();
            success = true;
            return result;
        } catch (McpException e) {
            throw new ToolCallException(e.getError().code() + ": " + e.getMessage());
        } catch (ToolCallException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolCallException("INTERNAL_ERROR: " + e.getMessage());
        } finally {
            long duration = System.currentTimeMillis() - start;
            metrics.recordToolInvocation(toolName, duration, success);
            auditService.logToolInvocation(toolName, targetId, null, duration, success);
        }
    }

    private static Map<String, Object> compactSummary(PerformanceSummary s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("targetId", s.targetId());
        m.put("window", s.window().label());
        m.put("providerStatus", s.providerStatus().name());
        m.put("source", s.source());
        m.put("http", s.http().asNullableMap());
        m.put("httpHistogramAvailable", s.http().histogramAvailable());
        m.put("database", s.database().asNullableMap());
        m.put("jvm", s.jvm().asNullableMap());
        m.put("cache", s.cache().asNullableMap());
        m.put("cluster", s.cluster().asNullableMap());
        m.put("runtime", s.runtime().asNullableMap());
        return m;
    }

    private static Map<String, Object> compactResult(SemanticMetricResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("metric", r.metric().name());
        m.put("window", r.window() == null ? null : r.window().label());
        m.put("value", r.value());
        m.put("unit", r.unit());
        m.put("availability", r.availability() == null ? null : r.availability().name());
        m.put("reason", r.reason());
        return m;
    }

    private static MetricCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            throw McpException.invalidArgument("category is required");
        }
        try {
            return MetricCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw McpException.invalidArgument("Unsupported metrics category: " + raw);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
