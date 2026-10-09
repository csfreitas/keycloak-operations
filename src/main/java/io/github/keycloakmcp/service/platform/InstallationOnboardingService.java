package io.github.keycloakmcp.service.platform;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.adapter.infrastructure.InfrastructureClientFactory;
import io.github.keycloakmcp.config.PlatformConfig;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.persistence.entity.*;
import io.github.keycloakmcp.persistence.mapper.TargetPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.TargetRepository;
import io.github.keycloakmcp.target.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class InstallationOnboardingService {
    @Inject TargetResolver resolver;
    @Inject TargetAuthorizationService authz;
    @Inject InfrastructureClientFactory clients;
    @Inject InstallationCandidateCollector collector;
    @Inject TargetRepository targets;
    @Inject TargetPersistenceMapper mapper;
    @Inject PlatformConfig platform;
    @Inject io.github.keycloakmcp.persistence.repository.AuditRepository audits;

    public record State(String targetId, String clusterId, String namespace, KubernetesInstallationBinding binding,
                        long revision, boolean managed, boolean canDiscover, boolean canConfirm) {}
    public record Candidate(String id, KubernetesInstallationBinding installation) {}
    public record Discovery(String runId, String targetId, String namespace, Instant expiresAt, List<Candidate> candidates) {}
    public record Confirmation(String runId, String candidateId) {}

    public State state(String id) {
        Target target = resolver.require(id);
        authz.assertAllowed(target, TargetPermission.READ);
        var entity = targets.findById(id);
        var infra = target.infrastructure();
        boolean configured = configured(target) && entity != null && persistentRegistry();
        return new State(id, infra == null ? null : infra.clusterId(), infra == null ? null : infra.namespace(),
                infra == null ? null : infra.installation(), entity == null ? 0 : entity.installationRevision,
                entity != null && entity.installationManaged,
                configured && authz.isAllowed(target, TargetPermission.DISCOVER),
                configured && authz.isAllowed(target, TargetPermission.DISCOVER) && authz.isAllowed(target, TargetPermission.BIND));
    }

    @Transactional
    public Discovery discover(String id) {
        Target target = authorized(id, TargetPermission.DISCOVER);
        var entity = locked(id);
        target = mapper.toDomain(entity);
        authz.assertAllowed(target, TargetPermission.DISCOVER);
        var cluster = cluster(target);
        String actor = authz.currentActor();
        // One fresh run per caller/target; expiry cleanup is restricted to this target.
        InstallationDiscoveryRunEntity.delete("targetId = ?1 and (actor = ?2 or expiresAt < ?3)", id, actor, Instant.now());
        if (InstallationDiscoveryRunEntity.count("targetId", id) >= 20) throw McpException.invalidArgument("Too many pending discovery runs; retry after expiry");
        var found = collector.discover(cluster, target.infrastructure().namespace());
        var run = new InstallationDiscoveryRunEntity();
        run.id = UUID.randomUUID().toString();
        run.targetId = id;
        run.actor = actor;
        run.contextHash = context(target, cluster);
        run.bindingRevision = entity.installationRevision;
        run.expiresAt = Instant.now().plusSeconds(600);
        run.candidates = new LinkedHashMap<>();
        found.forEach(b -> run.candidates.put(UUID.randomUUID().toString(), b));
        run.persist();
        return new Discovery(run.id, id, target.infrastructure().namespace(), run.expiresAt,
                run.candidates.entrySet().stream().map(e -> new Candidate(e.getKey(), e.getValue())).toList());
    }

    @Transactional
    public State confirm(String id, Confirmation request) {
        authorized(id, TargetPermission.BIND);
        if (request == null || request.runId() == null || request.candidateId() == null) throw McpException.invalidArgument("A discovery run and candidate are required");
        var entity = locked(id);
        var target = mapper.toDomain(entity);
        authz.assertAllowed(target, TargetPermission.READ);
        authz.assertAllowed(target, TargetPermission.DISCOVER);
        authz.assertAllowed(target, TargetPermission.BIND);
        var run = InstallationDiscoveryRunEntity.<InstallationDiscoveryRunEntity>findById(request.runId());
        if (run == null || !id.equals(run.targetId) || !authz.currentActor().equals(run.actor)
                || run.consumed || !run.expiresAt.isAfter(Instant.now()) || run.bindingRevision != entity.installationRevision) throw stale();
        var candidate = run.candidates.get(request.candidateId());
        var cluster = cluster(target);
        if (candidate == null || !run.contextHash.equals(context(target, cluster))
                || !collector.stillExists(cluster, target.infrastructure().namespace(), candidate)
                || !run.expiresAt.isAfter(Instant.now())) throw stale();
        var oldBinding = target.infrastructure().installation();
        entity.installationApiVersion = candidate.apiVersion();
        entity.installationKind = candidate.kind();
        entity.installationName = candidate.name();
        entity.installationUid = candidate.uid();
        entity.installationManaged = true;
        entity.installationRevision++;
        entity.updatedAt = Instant.now();
        run.consumed = true;
        // Required audit shares the transaction; unlike best-effort operational logging, failures roll back binding.
        var audit = new AuditEventEntity();
        audit.id = UUID.randomUUID().toString();
        audit.targetId = id;
        audit.source = "REST";
        audit.tool = "installation.confirm";
        audit.operation = "INSTALLATION_BOUND";
        audit.status = "SUCCESS";
        audit.createdAt = Instant.now();
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("actor", authz.currentActor());
        metadata.put("revision", entity.installationRevision);
        metadata.put("runId", run.id);
        metadata.put("previousUid", oldBinding == null ? null : oldBinding.uid());
        metadata.put("selectedUid", candidate.uid());
        audit.metadata = metadata;
        audits.persistAndFlush(audit);
        return state(id);
    }

    private Target authorized(String id, TargetPermission permission) {
        var target = resolver.require(id);
        authz.assertAllowed(target, TargetPermission.READ);
        authz.assertAllowed(target, permission);
        if (!configured(target) || !persistentRegistry()) throw McpException.unsupportedCapability("Installation onboarding requires a persisted target with an approved cluster connection");
        return target;
    }

    private TargetEntity locked(String id) {
        var entity = targets.findById(id, LockModeType.PESSIMISTIC_WRITE);
        if (entity == null) throw McpException.targetNotFound(id);
        return entity;
    }

    private ClusterClient cluster(Target target) {
        if (!configured(target)) throw McpException.unsupportedCapability("Explicit cluster connection required");
        return clients.resolve(target).orElseThrow(() -> McpException.unsupportedCapability("Cluster client unavailable"));
    }

    private static boolean configured(Target t) {
        var i = t.infrastructure();
        return i != null && (i.type() == InfrastructureType.KUBERNETES || i.type() == InfrastructureType.OPENSHIFT)
                && i.clusterId() != null && !i.clusterId().isBlank() && i.namespace() != null && !i.namespace().isBlank()
                && i.credentialRef() != null && !i.credentialRef().isBlank();
    }

    private boolean persistentRegistry() {
        return !"configuration".equalsIgnoreCase(platform.targetRegistry()) && !"config".equalsIgnoreCase(platform.targetRegistry());
    }

    private static String context(Target target, ClusterClient cluster) {
        try {
            var i = target.infrastructure();
            String value = java.util.stream.Stream.of(target.id().value(), target.keycloak().url(), i.type().name(), i.clusterId(),
                    i.namespace(), i.credentialRef(), cluster.kubernetes().getMasterUrl().toString())
                    .map(s -> s.length() + ":" + s).collect(java.util.stream.Collectors.joining());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw McpException.internal("Unable to validate connection context"); }
    }

    private static McpException stale() {
        return McpException.invalidArgument("Discovery expired, changed or is not available to this caller; discover and review again");
    }
}
