package io.github.keycloakmcp.target;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.config.McpRuntimeConfig;
import io.github.keycloakmcp.domain.error.McpException;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;

/**
 * Targets loaded from {@code mcp.targets.*} configuration.
 * Not the CDI default {@link TargetRegistry} — prefer
 * {@link CompositeTargetRegistry} for application wiring.
 */
@ApplicationScoped
@Typed(ConfigurationTargetRegistry.class)
public class ConfigurationTargetRegistry implements TargetRegistry {

    private static final Logger LOG = Logger.getLogger(ConfigurationTargetRegistry.class);

    private final McpRuntimeConfig runtimeConfig;
    private Map<String, Target> targets = Map.of();

    @Inject
    public ConfigurationTargetRegistry(McpRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    @PostConstruct
    void load() {
        Map<String, Target> loaded = new LinkedHashMap<>();
        Map<String, McpRuntimeConfig.TargetEntry> configured = runtimeConfig.targets();
        if (configured != null) {
            for (Map.Entry<String, McpRuntimeConfig.TargetEntry> entry : configured.entrySet()) {
                String id = entry.getKey();
                try {
                    Target target = toTarget(id, entry.getValue());
                    loaded.put(target.id().value(), target);
                } catch (RuntimeException e) {
                    throw McpException.invalidArgument(
                            "Invalid mcp.targets." + id + " configuration: " + e.getMessage());
                }
            }
        }
        // Preserve configuration insertion order (Map.copyOf does not).
        this.targets = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
        LOG.infof("Loaded %d MCP target(s) from configuration", targets.size());
    }

    @Override
    public List<Target> list() {
        return List.copyOf(new ArrayList<>(targets.values()));
    }

    @Override
    public Optional<Target> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(targets.get(id.trim()));
    }

    private static Target toTarget(String id, McpRuntimeConfig.TargetEntry entry) {
        TargetId targetId = TargetId.of(id);
        if (entry.displayName() == null || entry.displayName().isBlank()) {
            throw new IllegalArgumentException("display-name is required");
        }
        TargetType type = TargetType.parse(entry.type());
        TargetEnvironment environment = TargetEnvironment.parse(entry.environment());
        McpRuntimeConfig.KeycloakEntry kc = entry.keycloak();
        if (kc == null) {
            throw new IllegalArgumentException("keycloak block is required");
        }
        if (kc.url() == null || kc.url().isBlank()) {
            throw new IllegalArgumentException("keycloak.url is required");
        }
        if (kc.clientId() == null || kc.clientId().isBlank()) {
            throw new IllegalArgumentException("keycloak.client-id is required");
        }
        if (kc.credentialRef() == null || kc.credentialRef().isBlank()) {
            throw new IllegalArgumentException("keycloak.credential-ref is required");
        }
        String managementUrl = null;
        if (kc.managementUrl() != null && kc.managementUrl().isPresent()) {
            String raw = kc.managementUrl().get();
            if (raw != null) {
                String trimmed = raw.trim();
                if (!trimmed.isBlank()) {
                    managementUrl = trimmed;
                }
            }
        }
        KeycloakTargetConfiguration keycloak = new KeycloakTargetConfiguration(
                kc.url().trim(),
                kc.authRealm(),
                kc.clientId().trim(),
                kc.credentialRef().trim(),
                managementUrl);

        InfrastructureTargetConfiguration infrastructure = entry.infrastructure()
                .map(ConfigurationTargetRegistry::toInfrastructure)
                .orElse(null);

        ObservabilityTargetConfiguration observability = entry.observability()
                .map(ConfigurationTargetRegistry::toObservability)
                .orElse(null);

        Map<String, String> tags = entry.tags() == null ? Map.of() : Map.copyOf(entry.tags());

        return new Target(
                targetId,
                entry.displayName().trim(),
                type,
                environment,
                entry.enabled(),
                keycloak,
                infrastructure,
                observability,
                tags);
    }

    private static InfrastructureTargetConfiguration toInfrastructure(McpRuntimeConfig.InfrastructureEntry entry) {
        InfrastructureType type = InfrastructureType.parse(entry.type());
        return new InfrastructureTargetConfiguration(
                type,
                entry.clusterId().orElse(null),
                entry.namespace().orElse(null),
                entry.credentialRef().orElse(null));
    }

    private static ObservabilityTargetConfiguration toObservability(McpRuntimeConfig.ObservabilityEntry entry) {
        String metricsType = entry.metrics().flatMap(McpRuntimeConfig.ObservabilityEntry.MetricsEntry::type).orElse(null);
        String tracingType = entry.tracing().flatMap(McpRuntimeConfig.ObservabilityEntry.TracingEntry::type).orElse(null);
        String endpoint = entry.metrics()
                .flatMap(m -> firstPresent(m.endpointUrl(), m.endpoint()))
                .orElse(null);
        String credentialRef = entry.metrics()
                .flatMap(McpRuntimeConfig.ObservabilityEntry.MetricsEntry::credentialRef)
                .orElse(null);
        String namespace = entry.metrics()
                .flatMap(McpRuntimeConfig.ObservabilityEntry.MetricsEntry::namespace)
                .orElse(null);
        String scope = entry.metrics()
                .flatMap(McpRuntimeConfig.ObservabilityEntry.MetricsEntry::scope)
                .orElse("NAMESPACE");
        return new ObservabilityTargetConfiguration(
                metricsType, tracingType, blankToNull(endpoint), blankToNull(credentialRef),
                blankToNull(namespace), scope);
    }

    private static Optional<String> firstPresent(Optional<String> a, Optional<String> b) {
        if (a != null && a.isPresent() && a.get() != null && !a.get().isBlank()) {
            return a;
        }
        if (b != null && b.isPresent() && b.get() != null && !b.get().isBlank()) {
            return b;
        }
        return Optional.empty();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
