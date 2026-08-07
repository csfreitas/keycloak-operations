package io.github.keycloakmcp.persistence.mapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.keycloakmcp.persistence.entity.TargetEntity;
import io.github.keycloakmcp.target.InfrastructureTargetConfiguration;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.ObservabilityTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetType;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Maps between domain {@link Target} and {@link TargetEntity}.
 * Never maps secrets — only credential references.
 */
@ApplicationScoped
public class TargetPersistenceMapper {

    public TargetEntity toEntity(Target target) {
        Instant now = Instant.now();
        TargetEntity entity = new TargetEntity();
        apply(target, entity, now);
        entity.createdAt = now;
        return entity;
    }

    public void updateEntity(Target target, TargetEntity entity) {
        apply(target, entity, Instant.now());
    }

    public Target toDomain(TargetEntity entity) {
        KeycloakTargetConfiguration keycloak = new KeycloakTargetConfiguration(
                entity.keycloakUrl,
                entity.keycloakAuthRealm,
                entity.keycloakClientId,
                entity.keycloakCredentialRef);

        InfrastructureTargetConfiguration infrastructure = null;
        if (entity.infraType != null && !entity.infraType.isBlank()) {
            infrastructure = new InfrastructureTargetConfiguration(
                    InfrastructureType.parse(entity.infraType),
                    entity.infraClusterId,
                    entity.infraNamespace,
                    entity.infraCredentialRef);
        }

        ObservabilityTargetConfiguration observability = null;
        if (entity.observability != null && !entity.observability.isEmpty()) {
            Object metrics = entity.observability.get("metricsType");
            Object tracing = entity.observability.get("tracingType");
            observability = new ObservabilityTargetConfiguration(
                    metrics == null ? null : String.valueOf(metrics),
                    tracing == null ? null : String.valueOf(tracing));
        }

        Map<String, String> tags = entity.tags == null ? Map.of() : Map.copyOf(entity.tags);

        return new Target(
                TargetId.of(entity.id),
                entity.displayName,
                TargetType.parse(entity.productType),
                TargetEnvironment.parse(entity.environment),
                entity.enabled,
                keycloak,
                infrastructure,
                observability,
                tags);
    }

    private static void apply(Target target, TargetEntity entity, Instant updatedAt) {
        entity.id = target.id().value();
        entity.displayName = target.displayName();
        entity.productType = target.type().name();
        entity.environment = target.environment().name();
        entity.enabled = target.enabled();
        entity.keycloakUrl = target.keycloak().url();
        entity.keycloakAuthRealm = target.keycloak().authRealm();
        entity.keycloakClientId = target.keycloak().clientId();
        entity.keycloakCredentialRef = target.keycloak().credentialRef();

        if (target.infrastructure() != null) {
            entity.infraType = target.infrastructure().type() == null
                    ? null
                    : target.infrastructure().type().name();
            entity.infraClusterId = target.infrastructure().clusterId();
            entity.infraNamespace = target.infrastructure().namespace();
            entity.infraCredentialRef = target.infrastructure().credentialRef();
        } else {
            entity.infraType = null;
            entity.infraClusterId = null;
            entity.infraNamespace = null;
            entity.infraCredentialRef = null;
        }

        if (target.observability() != null) {
            Map<String, Object> obs = new LinkedHashMap<>();
            if (target.observability().metricsType() != null) {
                obs.put("metricsType", target.observability().metricsType());
            }
            if (target.observability().tracingType() != null) {
                obs.put("tracingType", target.observability().tracingType());
            }
            entity.observability = obs.isEmpty() ? null : obs;
        } else {
            entity.observability = null;
        }

        entity.tags = target.tags() == null ? new HashMap<>() : new HashMap<>(target.tags());
        entity.updatedAt = updatedAt;
    }
}
