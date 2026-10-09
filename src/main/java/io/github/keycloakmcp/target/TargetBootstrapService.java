package io.github.keycloakmcp.target;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.persistence.entity.AuditEventEntity;
import io.github.keycloakmcp.persistence.entity.TargetEntity;
import io.github.keycloakmcp.persistence.mapper.TargetPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.AuditRepository;
import io.github.keycloakmcp.persistence.repository.TargetRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Reconciles only configuration-owned targets. Definition changes and mandatory
 * system audit share a transaction; legacy ownership is never inferred from an ID.
 */
@ApplicationScoped
public class TargetBootstrapService {

    private static final Logger LOG = Logger.getLogger(TargetBootstrapService.class);

    private final ConfigurationTargetRegistry configurationRegistry;
    private final TargetRepository targetRepository;
    private final TargetPersistenceMapper mapper;
    private final AuditRepository audits;

    @Inject
    public TargetBootstrapService(
            ConfigurationTargetRegistry configurationRegistry,
            TargetRepository targetRepository,
            TargetPersistenceMapper mapper, AuditRepository audits) {
        this.configurationRegistry = configurationRegistry;
        this.targetRepository = targetRepository;
        this.mapper = mapper;
        this.audits = audits;
    }

    /**
     * Missing configured targets are inserted; only effective changes to explicitly
     * configuration-owned records are applied. Omission is never deletion/adoption.
     *
     * @return number of targets actually changed, excluding no-ops
     */
    @Transactional
    public int syncConfigTargetsToDatabase() {
        int count = 0;
        for (Target target : configurationRegistry.list().stream()
                .sorted(Comparator.comparing(t -> t.id().value())).toList()) {
            if (upsert(target)) count++;
        }
        LOG.infof("Reconciled %d changed configuration-owned target(s)", count);
        return count;
    }

    private boolean upsert(Target target) {
        var entity = targetRepository.findById(target.id().value(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (entity == null) {
            entity = mapper.toEntity(target);
            entity.registryOwner = RegistryOwner.CONFIGURATION;
            targetRepository.persist(entity);
            targetRepository.flush();
            audit(entity, null, 0, false, List.of("definition"));
            return true;
        }
        if (entity.registryOwner != RegistryOwner.CONFIGURATION) {
            throw new IllegalStateException("Configuration target ownership conflict; explicit adoption is required");
        }

        Target previous = mapper.toDomain(entity);
        // Compare the effective persistence representation, including management-url tags/defaults.
        TargetEntity desired = mapper.toEntity(target);
        boolean sameConnection = sameConnection(previous, mapper.toDomain(desired));
        var before = binding(previous);
        if (entity.installationManaged) {
            if (!sameConnection && desired.installationUid != null) {
                // Clearing now would reapply this stale explicit binding on the next identical sync.
                throw new IllegalStateException("Configuration cannot replace a managed binding while changing its connection; remove the configured binding first");
            }
            var keep = sameConnection ? before : null;
            desired.installationApiVersion = keep == null ? null : keep.apiVersion();
            desired.installationKind = keep == null ? null : keep.kind();
            desired.installationName = keep == null ? null : keep.name();
            desired.installationUid = keep == null ? null : keep.uid();
            desired.installationManaged = sameConnection;
        }
        Target effective = mapper.toDomain(desired);
        if (previous.equals(effective) && entity.installationManaged == desired.installationManaged) return false;

        long previousRegistryRevision = entity.registryRevision;
        long previousInstallationRevision = entity.installationRevision;
        long nextInstallationRevision = !sameConnection || !Objects.equals(before, binding(effective))
                ? Math.addExact(previousInstallationRevision, 1) : previousInstallationRevision;
        boolean invalidated = entity.installationManaged && !sameConnection;
        List<String> changedFields = changedFields(previous, effective);
        if (entity.installationManaged != desired.installationManaged) changedFields.add("installation.managed");

        mapper.updateEntity(effective, entity);
        entity.installationManaged = desired.installationManaged;
        entity.installationRevision = nextInstallationRevision;
        // @Version supplies the row revision. Flush before audit so it records the actual version.
        targetRepository.flush();
        audit(entity, previousRegistryRevision, previousInstallationRevision, invalidated, changedFields);
        return true;
    }

    private static boolean sameConnection(Target a, Target b) {
        var x = a.infrastructure();
        var y = b.infrastructure();
        return Objects.equals(a.keycloak(), b.keycloak())
                && (x == null ? y == null : y != null && x.type() == y.type()
                && Objects.equals(x.clusterId(), y.clusterId())
                && Objects.equals(x.namespace(), y.namespace())
                && Objects.equals(x.credentialRef(), y.credentialRef()));
    }

    private static KubernetesInstallationBinding binding(Target target) {
        return target.infrastructure() == null ? null : target.infrastructure().installation();
    }

    private static List<String> changedFields(Target before, Target after) {
        List<String> fields = new ArrayList<>();
        changed(fields, "displayName", before.displayName(), after.displayName());
        changed(fields, "productType", before.type(), after.type());
        changed(fields, "environment", before.environment(), after.environment());
        changed(fields, "enabled", before.enabled(), after.enabled());
        changed(fields, "keycloak.url", before.keycloak().url(), after.keycloak().url());
        changed(fields, "keycloak.authRealm", before.keycloak().authRealm(), after.keycloak().authRealm());
        changed(fields, "keycloak.clientId", before.keycloak().clientId(), after.keycloak().clientId());
        changed(fields, "keycloak.credentialRef", before.keycloak().credentialRef(), after.keycloak().credentialRef());
        changed(fields, "keycloak.managementUrl", before.keycloak().managementUrl(), after.keycloak().managementUrl());
        changed(fields, "infrastructure", before.infrastructure(), after.infrastructure());
        changed(fields, "observability", before.observability(), after.observability());
        changed(fields, "tags", before.tags(), after.tags());
        return fields;
    }

    private static void changed(List<String> fields, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) fields.add(field);
    }

    private void audit(TargetEntity entity, Long previousRegistryRevision, long previousInstallationRevision,
            boolean invalidated, List<String> changedFields) {
        var audit = new AuditEventEntity();
        audit.id = UUID.randomUUID().toString();
        audit.targetId = entity.id;
        audit.source = "SYSTEM";
        audit.tool = "registry.bootstrap";
        audit.operation = previousRegistryRevision == null ? "TARGET_CONFIG_CREATED" : "TARGET_CONFIG_UPDATED";
        audit.status = "SUCCESS";
        audit.createdAt = Instant.now();
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("origin", "CONFIGURATION_BOOTSTRAP");
        metadata.put("actorKind", "SYSTEM");
        metadata.put("owner", RegistryOwner.CONFIGURATION.name());
        metadata.put("previousRegistryRevision", previousRegistryRevision);
        metadata.put("registryRevision", entity.registryRevision);
        metadata.put("previousInstallationRevision", previousInstallationRevision);
        metadata.put("installationRevision", entity.installationRevision);
        metadata.put("bindingInvalidated", invalidated);
        metadata.put("changedFields", List.copyOf(changedFields));
        audit.metadata = metadata;
        // Required even when optional operational audit is disabled. Failure rolls back the whole sync.
        audits.persistAndFlush(audit);
    }
}
