package io.github.keycloakmcp.service.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.github.keycloakmcp.config.PlatformAuthorizationConfig;
import io.github.keycloakmcp.config.RegistryAdministrationConfig;
import io.github.keycloakmcp.domain.error.McpException;
import io.quarkus.runtime.Startup;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Additive administration boundary for local REST preflight only. This grants no
 * target permission, registration write, discovery, binding or MCP capability.
 * The OIDC extension, not this policy, verifies the JWT signature/audience/expiry.
 */
@Startup
@ApplicationScoped
public class RegistryPreflightPolicy {
    private static final int MAX_ADMINISTRATORS = 20;
    private static final int MAX_CLIENTS = 20;
    private static final int MAX_CLAIM_LENGTH = 2048;

    private final boolean enabled;
    private final List<Administrator> administrators;
    private final PlatformAuthorizationConfig authorization;
    private final SecurityIdentity identity;

    private record Administrator(String issuer, String subject, String role, Set<String> restClients) { }

    @Inject
    public RegistryPreflightPolicy(RegistryAdministrationConfig config,
            PlatformAuthorizationConfig authorization, SecurityIdentity identity) {
        this.authorization = authorization;
        this.identity = identity;
        try {
            enabled = config.enabled();
            Map<String, RegistryAdministrationConfig.Administrator> entries = config.administrators();
            if (entries == null || entries.size() > MAX_ADMINISTRATORS) throw invalidConfig();
            List<Administrator> validated = new ArrayList<>();
            entries.forEach((id, entry) -> {
                if (!handle(id) || entry == null) throw invalidConfig();
                String issuer = entry.issuer(), subject = entry.subject(), role = entry.role();
                Set<String> clients = entry.restClientIds();
                if (!claim(issuer) || !claim(subject) || !identifier(role)
                        || clients == null || clients.isEmpty() || clients.size() > MAX_CLIENTS
                        || !clients.stream().allMatch(RegistryPreflightPolicy::identifier)) throw invalidConfig();
                validated.add(new Administrator(issuer, subject, role, Set.copyOf(clients)));
            });
            administrators = List.copyOf(validated);
        } catch (RuntimeException malformedConfiguration) {
            // Configuration diagnostics may contain identity or source values. Do not retain the cause.
            throw invalidConfig();
        }
    }

    /** Rechecks the current framework identity every time; accepts no caller-supplied actor or claims. */
    public void assertAllowed() {
        if (!enabled) throw denied();
        String issuer;
        String subject;
        String client;
        try {
            if (authorization == null || !"authenticated".equals(authorization.mode())
                    || identity == null || identity.isAnonymous()
                    || !(identity.getPrincipal() instanceof JsonWebToken jwt)) throw unauthenticated();
            issuer = jwt.getIssuer();
            subject = jwt.getSubject();
            Object authorizedParty = jwt.getClaim("azp");
            if (!claim(issuer) || !claim(subject)
                    || !(authorizedParty instanceof String value) || !identifier(value)) throw unauthenticated();
            client = value;
        } catch (RuntimeException malformedIdentity) {
            throw unauthenticated();
        }
        try {
            for (Administrator administrator : administrators) {
                if (administrator.issuer().equals(issuer) && administrator.subject().equals(subject)
                        && administrator.restClients().contains(client) && identity.hasRole(administrator.role())) {
                    return;
                }
            }
        } catch (RuntimeException failedAuthorization) {
            throw denied();
        }
        throw denied();
    }

    private static boolean handle(String value) {
        return value != null && value.matches("[a-z][a-z0-9-]{0,63}");
    }

    private static boolean identifier(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:@-]{0,127}");
    }

    private static boolean claim(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_CLAIM_LENGTH
                && value.equals(value.strip()) && value.indexOf('*') < 0
                && value.chars().noneMatch(Character::isISOControl);
    }

    private static IllegalStateException invalidConfig() {
        return new IllegalStateException("Invalid registry administration configuration");
    }

    private static McpException unauthenticated() {
        return McpException.authenticationFailed("Registry preflight requires an authenticated OIDC identity and client");
    }

    private static McpException denied() {
        return McpException.authorizationFailed("Registry preflight is not authorized");
    }
}
