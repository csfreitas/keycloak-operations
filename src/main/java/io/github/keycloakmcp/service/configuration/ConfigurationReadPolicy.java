package io.github.keycloakmcp.service.configuration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.github.keycloakmcp.config.ConfigurationReadConfig;
import io.github.keycloakmcp.config.PlatformAuthorizationConfig;
import io.github.keycloakmcp.domain.configuration.ConfigurationKind;
import io.github.keycloakmcp.domain.configuration.ConfigurationScope;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.target.TargetId;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** New read channel only. Legacy target grants are intentionally neither widened nor narrowed. */
@ApplicationScoped
public class ConfigurationReadPolicy {
    public enum Channel { REST, MCP }
    public record Caller(String actorFingerprint, String clientId, String clientFingerprint) { }
    public record Grant(ConfigurationScope descriptor, String realmId, String clientId,
            String role, Set<String> restClients, Set<String> mcpClients) {
        public Grant { restClients = Set.copyOf(restClients); mcpClients = Set.copyOf(mcpClients); }
    }

    static final Set<String> REALM_FIELDS = Set.of("enabled", "registrationAllowed",
            "resetPasswordAllowed", "bruteForceProtected", "verifyEmail");
    static final Set<String> CLIENT_FIELDS = Set.of("enabled", "publicClient", "standardFlowEnabled",
            "implicitFlowEnabled", "directAccessGrantsEnabled", "serviceAccountsEnabled");
    private final Map<String, Grant> grants;
    private final PlatformAuthorizationConfig authorization;
    private final SecurityIdentity identity;

    @Inject
    public ConfigurationReadPolicy(ConfigurationReadConfig config, PlatformAuthorizationConfig authorization,
            SecurityIdentity identity) {
        this.authorization = authorization;
        this.identity = identity;
        TreeMap<String, Grant> validated = new TreeMap<>();
        if (config.scopes().size() > 100) throw invalidConfig();
        config.scopes().forEach((id, scope) -> {
            if (!handle(id) || !identifier(scope.role()) || !identifier(scope.realm())
                    || !identifier(scope.realmId()) || scope.kind() == null) throw invalidConfig();
            try {
                if (scope.targetId() == null || scope.targetId().length() > 128) throw invalidConfig();
                if (!TargetId.of(scope.targetId()).value().equals(scope.targetId())) throw invalidConfig();
            } catch (IllegalArgumentException exception) { throw invalidConfig(); }
            boolean client = scope.kind() == ConfigurationKind.CLIENT;
            if (client != scope.clientId().isPresent()
                    || (client && !identifier(scope.clientId().orElseThrow()))) throw invalidConfig();
            Set<String> allowed = client ? CLIENT_FIELDS : REALM_FIELDS;
            if (scope.fields().isEmpty() || !allowed.containsAll(scope.fields())) throw invalidConfig();
            Set<String> rest = scope.restClientIds().orElse(Set.of());
            Set<String> mcp = scope.mcpClientIds().orElse(Set.of());
            if (rest.size() > 20 || mcp.size() > 20 || !rest.stream().allMatch(ConfigurationReadPolicy::identifier)
                    || !mcp.stream().allMatch(ConfigurationReadPolicy::identifier)) throw invalidConfig();
            validated.put(id, new Grant(new ConfigurationScope(id, scope.targetId(), scope.realm(), scope.kind(),
                    scope.fields().stream().sorted().toList()), scope.realmId(), scope.clientId().orElse(null),
                    scope.role(), Set.copyOf(rest), Set.copyOf(mcp)));
        });
        grants = java.util.Collections.unmodifiableMap(validated);
    }

    /** Only the framework-verified JWT principal supplies identity; no request/clientInfo actor claims. */
    public Caller caller() {
        if (!"authenticated".equals(authorization.mode()) || identity.isAnonymous()
                || !(identity.getPrincipal() instanceof JsonWebToken jwt)) throw unauthenticated();
        try {
            String issuer = jwt.getIssuer(), subject = jwt.getSubject();
            Object authorizedParty = jwt.getClaim("azp");
            if (!boundedClaim(issuer) || !boundedClaim(subject)
                    || !(authorizedParty instanceof String client) || !identifier(client)) throw unauthenticated();
            // Length framing prevents delimiter ambiguity; hashes avoid retaining raw issuer/subject or client names.
            return new Caller(fingerprint(issuer.length() + ":" + issuer + subject.length() + ":" + subject),
                    client, fingerprint(client));
        } catch (RuntimeException malformedIdentity) {
            throw unauthenticated();
        }
    }

    public List<ConfigurationScope> list(Channel channel, Caller caller) {
        if (!caller().equals(caller)) throw denied();
        return grants.values().stream().filter(grant -> allowed(grant, channel, caller))
                .map(Grant::descriptor).toList();
    }

    public Grant require(String scopeId, Channel channel, Caller caller) {
        if (!caller().equals(caller)) throw denied();
        Grant grant = handle(scopeId) ? grants.get(scopeId) : null;
        if (grant == null || !allowed(grant, channel, caller)) throw denied();
        return grant;
    }

    public void reauthorize(Grant grant, Channel channel, Caller original) {
        Caller current = caller();
        if (!original.equals(current) || require(grant.descriptor().scopeId(), channel, current) != grant) throw denied();
    }

    private boolean allowed(Grant grant, Channel channel, Caller caller) {
        return channel != null && identity.hasRole(grant.role())
                && (channel == Channel.REST ? grant.restClients() : grant.mcpClients()).contains(caller.clientId());
    }

    private static boolean handle(String value) { return value != null && value.matches("[a-z][a-z0-9-]{0,63}"); }
    private static boolean identifier(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:@-]{0,127}");
    }
    private static boolean boundedClaim(String value) { return value != null && !value.isBlank() && value.length() <= 2048; }
    private static IllegalStateException invalidConfig() { return new IllegalStateException("Invalid configuration read scope"); }
    private static McpException unauthenticated() { return McpException.authenticationFailed("Configuration reads require an authenticated OIDC identity and client"); }
    private static McpException denied() { return McpException.authorizationFailed("Configuration scope is not authorized"); }
    private static String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
