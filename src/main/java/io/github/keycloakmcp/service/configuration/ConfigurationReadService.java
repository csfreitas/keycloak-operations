package io.github.keycloakmcp.service.configuration;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;

import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.domain.configuration.ConfigurationKind;
import io.github.keycloakmcp.domain.configuration.ConfigurationObservation;
import io.github.keycloakmcp.domain.configuration.ConfigurationScope;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.platform.AuditSource;
import io.github.keycloakmcp.service.configuration.ConfigurationReadPolicy.Caller;
import io.github.keycloakmcp.service.configuration.ConfigurationReadPolicy.Channel;
import io.github.keycloakmcp.service.configuration.ConfigurationReadPolicy.Grant;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Shared REST/MCP service: exact configured resources, closed Boolean facts, no persistence of evidence. */
@ApplicationScoped
public class ConfigurationReadService {
    private static final long TIMEOUT_MS = 10_000;
    private final ConfigurationReadPolicy policy;
    private final TargetResolver targets;
    private final StableAdminApiAdapter admin;
    private final AuditService audit;

    @Inject
    public ConfigurationReadService(ConfigurationReadPolicy policy, TargetResolver targets,
            StableAdminApiAdapter admin, AuditService audit) {
        this.policy = policy; this.targets = targets; this.admin = admin; this.audit = audit;
    }

    public List<ConfigurationScope> list(Channel channel) {
        Caller caller = policy.caller();
        List<ConfigurationScope> scopes = policy.list(channel, caller);
        audit.recordConfigurationRead(source(channel), "configuration-scopes", null, null,
                caller.actorFingerprint(), caller.clientFingerprint(), "SUCCESS", 0, UUID.randomUUID().toString());
        return scopes;
    }

    public ConfigurationObservation read(String scopeId, Channel channel) {
        Caller caller = policy.caller();
        String observationId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        Grant grant = null;
        String status = "DENIED";
        try {
            // No target lookup/provider I/O before the exact role + client + channel grant.
            grant = policy.require(scopeId, channel, caller);
            status = "UNAVAILABLE";
            Map<String, Boolean> facts = collect(grant);
            status = "DENIED";
            policy.reauthorize(grant, channel, caller);
            List<String> missing = facts.entrySet().stream().filter(entry -> entry.getValue() == null)
                    .map(Map.Entry::getKey).toList();
            status = missing.isEmpty() ? "COMPLETE" : "PARTIAL";
            return new ConfigurationObservation("1.0", observationId, grant.descriptor(), Instant.now(),
                    "KEYCLOAK_ADMIN_API", "UNKNOWN", status, facts, missing);
        } finally {
            audit.recordConfigurationRead(source(channel), "configuration-read",
                    grant == null ? null : grant.descriptor().targetId(),
                    grant == null ? null : grant.descriptor().scopeId(), caller.actorFingerprint(),
                    caller.clientFingerprint(), status, (System.nanoTime() - started) / 1_000_000, observationId);
        }
    }

    private Map<String, Boolean> collect(Grant grant) {
        ConfigurationScope scope = grant.descriptor();
        try (var budget = CollectionBudget.open(scope.targetId(), TIMEOUT_MS)) {
            Target target = targets.require(scope.targetId());
            RealmRepresentation realm = admin.getRealm(target, scope.realm());
            checkRealm(grant, realm);
            ClientRepresentation client = null;
            if (scope.kind() == ConfigurationKind.CLIENT) {
                client = admin.getClient(target, scope.realm(), grant.clientId());
                if (client == null || !grant.clientId().equals(client.getId())) throw unavailable();
                // Realm names are locators, not identity: reject delete/recreate during a client read.
                checkRealm(grant, admin.getRealm(target, scope.realm()));
            }
            Map<String, Boolean> facts = new LinkedHashMap<>();
            for (String field : scope.fields()) {
                facts.put(field, scope.kind() == ConfigurationKind.REALM ? realmFact(realm, field) : clientFact(client, field));
            }
            if (!target.equals(targets.require(scope.targetId()))) throw unavailable();
            budget.budget().checkpoint();
            return facts;
        } catch (RuntimeException failure) {
            // Never return a provider status/body, raw representation, locator or exception graph.
            throw unavailable();
        }
    }

    private static void checkRealm(Grant grant, RealmRepresentation realm) {
        if (realm == null || !grant.realmId().equals(realm.getId())
                || !grant.descriptor().realm().equals(realm.getRealm())) throw unavailable();
    }

    private static Boolean realmFact(RealmRepresentation realm, String field) {
        return switch (field) {
            case "enabled" -> realm.isEnabled();
            case "registrationAllowed" -> realm.isRegistrationAllowed();
            case "resetPasswordAllowed" -> realm.isResetPasswordAllowed();
            case "bruteForceProtected" -> realm.isBruteForceProtected();
            case "verifyEmail" -> realm.isVerifyEmail();
            default -> throw unavailable();
        };
    }

    private static Boolean clientFact(ClientRepresentation client, String field) {
        return switch (field) {
            case "enabled" -> client.isEnabled();
            case "publicClient" -> client.isPublicClient();
            case "standardFlowEnabled" -> client.isStandardFlowEnabled();
            case "implicitFlowEnabled" -> client.isImplicitFlowEnabled();
            case "directAccessGrantsEnabled" -> client.isDirectAccessGrantsEnabled();
            case "serviceAccountsEnabled" -> client.isServiceAccountsEnabled();
            default -> throw unavailable();
        };
    }

    private static McpException unavailable() { return McpException.keycloakUnavailable("Configuration evidence is unavailable", null); }
    private static AuditSource source(Channel channel) { return channel == Channel.MCP ? AuditSource.MCP : AuditSource.REST; }
}
