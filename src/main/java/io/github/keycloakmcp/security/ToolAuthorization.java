package io.github.keycloakmcp.security;

import java.util.Locale;
import java.util.Set;

import io.github.keycloakmcp.config.McpRuntimeConfig;
import io.github.keycloakmcp.domain.error.McpException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ToolAuthorization {

    private static final Set<String> WRITE_VERBS = Set.of(
            "create", "update", "delete", "write", "set", "add", "remove", "patch", "reset", "import", "export");

    private final McpRuntimeConfig runtimeConfig;

    @Inject
    public ToolAuthorization(McpRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public boolean isReadOnlyMode() {
        return runtimeConfig.readOnly();
    }

    /**
     * Asserts that the named tool may execute under the current read-only policy.
     * Write-like tool names are rejected when {@code mcp.read-only=true}.
     */
    public void assertReadOnlyOperation(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            throw McpException.invalidArgument("toolName must not be blank");
        }
        if (isReadOnlyMode() && looksLikeWriteTool(toolName)) {
            throw McpException.authorizationFailed(
                    "Tool '" + toolName + "' is not permitted while mcp.read-only=true");
        }
    }

    private static boolean looksLikeWriteTool(String toolName) {
        String normalized = toolName.toLowerCase(Locale.ROOT);
        for (String verb : WRITE_VERBS) {
            if (normalized.contains("_" + verb + "_")
                    || normalized.startsWith(verb + "_")
                    || normalized.endsWith("_" + verb)
                    || normalized.contains("." + verb)) {
                return true;
            }
        }
        return false;
    }
}
