package io.github.keycloakmcp.mcp;

import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Public MCP diagnostics are copies, never the original throwable graph. */
@ApplicationScoped
public class McpToolErrorProjector {

    private static final String INTERNAL_FAILURE = "INTERNAL_ERROR: tool operation failed";

    private final SensitiveDataFilter sensitiveDataFilter;

    @Inject
    public McpToolErrorProjector(SensitiveDataFilter sensitiveDataFilter) {
        this.sensitiveDataFilter = sensitiveDataFilter;
    }

    public ToolCallException project(Exception failure) {
        if (failure instanceof McpException domainFailure) {
            String code = domainFailure.getError().code().name();
            String message = domainFailure.getMessage();
            return new ToolCallException(code + ": "
                    + (message == null ? code : sensitiveDataFilter.redactString(message)));
        }
        if (failure instanceof ToolCallException toolFailure) {
            String message = toolFailure.getMessage();
            return new ToolCallException(message == null
                    ? INTERNAL_FAILURE : sensitiveDataFilter.redactString(message));
        }
        // Unknown failures may contain unlabeled credentials, provider bodies or internals.
        // Do not copy their message, cause or suppressed exceptions to the public boundary.
        return new ToolCallException(INTERNAL_FAILURE);
    }
}
