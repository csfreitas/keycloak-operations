package io.github.keycloakmcp.observability;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class McpMetrics {

    private final MeterRegistry registry;
    private final Counter toolInvocations;
    private final Counter toolErrors;
    private final Timer toolDuration;
    private final Counter keycloakAdminRequests;
    private final Counter assessmentRuns;
    private final Counter assessmentFindings;

    @Inject
    public McpMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.toolInvocations = Counter.builder("mcp_tool_invocations_total")
                .description("Total MCP tool invocations")
                .register(registry);
        this.toolErrors = Counter.builder("mcp_tool_errors_total")
                .description("Total MCP tool errors")
                .register(registry);
        this.toolDuration = Timer.builder("mcp_tool_duration_seconds")
                .description("MCP tool execution duration")
                .register(registry);
        this.keycloakAdminRequests = Counter.builder("keycloak_admin_requests_total")
                .description("Total Keycloak Admin API requests")
                .register(registry);
        this.assessmentRuns = Counter.builder("assessment_runs_total")
                .description("Total assessment runs")
                .register(registry);
        this.assessmentFindings = Counter.builder("assessment_findings_total")
                .description("Total assessment findings produced")
                .register(registry);
    }

    public void recordToolInvocation(String toolName, long durationMs, boolean success) {
        toolInvocations.increment();
        toolDuration.record(durationMs, TimeUnit.MILLISECONDS);
        if (!success) {
            toolErrors.increment();
        }
    }

    public void recordKeycloakAdminRequest(String operation) {
        keycloakAdminRequests.increment();
    }

    /**
     * Records a Keycloak Admin API request tagged by target and operation.
     * <p>
     * <b>Cardinality note:</b> the {@code target} tag scales with the number of configured
     * targets. Monitor series count in production if many targets are registered.
     */
    public void recordKeycloakAdminRequest(String targetId, String operation) {
        keycloakAdminRequests.increment();
        String target = (targetId == null || targetId.isBlank()) ? "-" : targetId;
        String op = (operation == null || operation.isBlank()) ? "-" : operation;
        Counter.builder("keycloak_admin_requests_by_target_total")
                .description("Keycloak Admin API requests tagged by target (monitor cardinality)")
                .tag("target", target)
                .tag("operation", op)
                .register(registry)
                .increment();
    }

    public void recordAssessmentRun(int findingCount) {
        assessmentRuns.increment();
        if (findingCount > 0) {
            assessmentFindings.increment(findingCount);
        }
    }
}
