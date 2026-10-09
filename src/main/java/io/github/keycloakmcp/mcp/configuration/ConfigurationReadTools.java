package io.github.keycloakmcp.mcp.configuration;

import java.util.List;
import java.util.function.Supplier;

import io.github.keycloakmcp.domain.configuration.ConfigurationObservation;
import io.github.keycloakmcp.domain.configuration.ConfigurationScope;
import io.github.keycloakmcp.mcp.McpToolErrorProjector;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.service.configuration.ConfigurationReadPolicy.Channel;
import io.github.keycloakmcp.service.configuration.ConfigurationReadService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;

public class ConfigurationReadTools {
    @Inject ConfigurationReadService service;
    @Inject McpToolErrorProjector errors;
    @Inject McpMetrics metrics;

    @Tool(name = "keycloak_list_configuration_scopes", description = "List explicitly authorized configuration read handles. Not a realm/client inventory or installation discovery. Requires an approved authenticated MCP client.")
    public List<ConfigurationScope> list() {
        return invoke("keycloak_list_configuration_scopes", () -> service.list(Channel.MCP));
    }

    @Tool(name = "keycloak_read_configuration", description = "Read only the configured Boolean realm/client facts for an authorized scope. Unknown is not false. No health score, secrets, users or arbitrary API access. Product version remains UNKNOWN.")
    public ConfigurationObservation read(@ToolArg(description = "Exact scopeId from keycloak_list_configuration_scopes; never a URL or caller-defined resource") String scopeId) {
        return invoke("keycloak_read_configuration", () -> service.read(scopeId, Channel.MCP));
    }

    private <T> T invoke(String tool, Supplier<T> operation) {
        long started = System.nanoTime();
        boolean success = false;
        try {
            T result = operation.get(); success = true; return result;
        } catch (RuntimeException failure) {
            throw errors.project(failure);
        } finally {
            metrics.recordToolInvocation(tool, (System.nanoTime() - started) / 1_000_000, success);
        }
    }
}
