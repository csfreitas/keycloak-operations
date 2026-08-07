package io.github.keycloakmcp.mcp.target;

import java.util.List;
import java.util.Locale;

import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.target.TargetDetails;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetMapper;
import io.github.keycloakmcp.target.TargetRegistry;
import io.github.keycloakmcp.target.TargetResolver;
import io.github.keycloakmcp.target.TargetSummary;
import io.github.keycloakmcp.target.TargetType;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.inject.Inject;

/**
 * Target discovery tools. Never expose URLs, credentialRef, or secrets.
 */
public class TargetTools {

    private static final String TARGET_ID_HINT =
            "Identifies a previously registered Keycloak/RHBK environment. "
                    + "Use keycloak_list_targets when the target is unknown. "
                    + "Never pass arbitrary URLs.";

    @Inject
    TargetRegistry targetRegistry;

    @Inject
    TargetResolver targetResolver;

    @Inject
    AuditService auditService;

    @Inject
    McpMetrics metrics;

    @Inject
    ToolAuthorization toolAuthorization;

    @Tool(
            name = "keycloak_list_targets",
            description = "List registered Keycloak/RHBK targets (sanitized metadata only). "
                    + "Call this before other tools when targetId is unknown.")
    public List<TargetSummary> keycloakListTargets() {
        return invoke("keycloak_list_targets", null, () -> TargetMapper.toSummaries(targetRegistry.list()));
    }

    @Tool(
            name = "keycloak_get_target",
            description = "Get sanitized metadata for a registered target. " + TARGET_ID_HINT)
    public TargetDetails keycloakGetTarget(
            @ToolArg(description = TARGET_ID_HINT) String targetId) {
        return invoke(
                "keycloak_get_target",
                targetId,
                () -> TargetMapper.toDetails(targetResolver.require(targetId)));
    }

    @Tool(
            name = "keycloak_find_targets",
            description = "Find registered targets by optional product type and/or environment. "
                    + "Returns sanitized metadata only.")
    public List<TargetSummary> keycloakFindTargets(
            @ToolArg(
                    description = "Optional product filter: KEYCLOAK or RHBK",
                    defaultValue = "")
                    String product,
            @ToolArg(
                    description = "Optional environment filter: DEV, TEST, HML, STAGING, PRD, UNKNOWN",
                    defaultValue = "")
                    String environment) {
        return invoke("keycloak_find_targets", null, () -> {
            TargetType typeFilter = parseType(product);
            TargetEnvironment envFilter = parseEnvironment(environment);
            return targetRegistry.list().stream()
                    .filter(t -> typeFilter == null || t.type() == typeFilter)
                    .filter(t -> envFilter == null || t.environment() == envFilter)
                    .map(TargetMapper::toSummary)
                    .toList();
        });
    }

    private static TargetType parseType(String product) {
        if (product == null || product.isBlank()) {
            return null;
        }
        try {
            return TargetType.valueOf(product.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw McpException.invalidArgument("Invalid product filter: " + product);
        }
    }

    private static TargetEnvironment parseEnvironment(String environment) {
        if (environment == null || environment.isBlank()) {
            return null;
        }
        try {
            return TargetEnvironment.valueOf(environment.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw McpException.invalidArgument("Invalid environment filter: " + environment);
        }
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
}
