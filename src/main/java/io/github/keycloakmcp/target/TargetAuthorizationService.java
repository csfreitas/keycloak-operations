package io.github.keycloakmcp.target;

import io.github.keycloakmcp.config.McpRuntimeConfig;
import io.github.keycloakmcp.domain.error.McpException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Target-scoped authorization.
 * <p>
 * {@link TargetPermission#READ}, {@link TargetPermission#ASSESS}, and {@link TargetPermission#PLAN}
 * are allowed for enabled targets. {@link TargetPermission#WRITE} and {@link TargetPermission#ADMIN}
 * are denied while global read-only mode is active.
 */
@ApplicationScoped
public class TargetAuthorizationService {

    private final McpRuntimeConfig runtimeConfig;

    @Inject
    public TargetAuthorizationService(McpRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public void assertAllowed(Target target, TargetPermission permission) {
        if (target == null) {
            throw McpException.invalidArgument("target must not be null");
        }
        if (permission == null) {
            throw McpException.invalidArgument("permission must not be null");
        }
        if (!target.enabled()) {
            throw McpException.targetDisabled(target.id().value());
        }
        switch (permission) {
            case READ, ASSESS, PLAN -> {
                // allowed for enabled targets
            }
            case WRITE, ADMIN -> {
                if (runtimeConfig.readOnly()) {
                    throw McpException.targetUnauthorized(target.id().value());
                }
            }
        }
    }
}
