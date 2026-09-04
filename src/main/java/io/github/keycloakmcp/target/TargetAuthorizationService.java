package io.github.keycloakmcp.target;

import io.github.keycloakmcp.config.McpRuntimeConfig;
import io.github.keycloakmcp.config.PlatformAuthorizationConfig;
import io.github.keycloakmcp.domain.error.McpException;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Shared REST/MCP target boundary. Authenticated identities receive only explicitly
 * configured role/target/permission grants. Local lab mode is deliberately unauthenticated.
 */
@ApplicationScoped
public class TargetAuthorizationService {

    private final McpRuntimeConfig runtimeConfig;
    private final PlatformAuthorizationConfig authorizationConfig;
    private final SecurityIdentity identity;

    @Inject
    public TargetAuthorizationService(McpRuntimeConfig runtimeConfig,
            PlatformAuthorizationConfig authorizationConfig, SecurityIdentity identity) {
        this.runtimeConfig = runtimeConfig;
        this.authorizationConfig = authorizationConfig;
        this.identity = identity;
    }

    public void assertAllowed(Target target, TargetPermission permission) {
        if (target == null) {
            throw McpException.invalidArgument("target must not be null");
        }
        if (permission == null) {
            throw McpException.invalidArgument("permission must not be null");
        }
        assertSession();
        if (!isLocalLab() && authorizationConfig.grants().entrySet().stream().noneMatch(entry ->
                identity.hasRole(entry.getKey())
                        && entry.getValue().targets().contains(target.id().value())
                        && entry.getValue().permissions().contains(permission))) {
            throw McpException.targetUnauthorized(target.id().value());
        }
        if (!target.enabled()) {
            throw McpException.targetDisabled(target.id().value());
        }
        switch (permission) {
            case READ, ASSESS, PLAN -> { }
            case APPROVE, WRITE, ADMIN -> {
                if (runtimeConfig.readOnly()) {
                    throw McpException.targetUnauthorized(target.id().value());
                }
            }
        }
    }

    /** Used only for filtered discovery, never to turn programming/provider errors into an empty fleet. */
    public boolean isAllowed(Target target, TargetPermission permission) {
        try {
            assertAllowed(target, permission);
            return true;
        } catch (McpException e) {
            return switch (e.getCode()) {
                case TARGET_DISABLED, TARGET_NOT_AUTHORIZED -> false;
                default -> throw e;
            };
        }
    }

    public boolean isLocalLab() {
        return "local-lab".equals(authorizationConfig.mode());
    }

    /** Never accepts caller-provided actor/approver text as identity. */
    public String currentActor() {
        assertSession();
        if (isLocalLab()) {
            return "local-lab";
        }
        if (identity.getPrincipal() instanceof JsonWebToken jwt) {
            if (jwt.getSubject() == null || jwt.getSubject().isBlank()
                    || jwt.getIssuer() == null || jwt.getIssuer().isBlank()) {
                throw McpException.authenticationFailed("Authenticated token requires issuer and subject");
            }
            return jwt.getIssuer() + "#" + jwt.getSubject();
        }
        return identity.getPrincipal().getName();
    }

    public void assertSession() {
        if (isLocalLab()) {
            return;
        }
        if (!"authenticated".equals(authorizationConfig.mode())) {
            throw McpException.authorizationFailed("Unknown platform authorization mode");
        }
        if (identity == null || identity.isAnonymous() || identity.getPrincipal() == null
                || identity.getPrincipal().getName() == null || identity.getPrincipal().getName().isBlank()) {
            throw McpException.authenticationFailed("An authenticated platform identity is required");
        }
    }
}
