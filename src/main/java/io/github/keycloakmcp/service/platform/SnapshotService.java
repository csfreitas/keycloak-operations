package io.github.keycloakmcp.service.platform;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.domain.common.ServerInfo;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.domain.platform.SnapshotSummary;
import io.github.keycloakmcp.persistence.entity.EnvironmentSnapshotEntity;
import io.github.keycloakmcp.persistence.entity.InventorySnapshotEntity;
import io.github.keycloakmcp.persistence.mapper.PlatformPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.SnapshotRepository;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.ServerInfoService;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SnapshotService {

    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final ServerInfoService serverInfoService;
    private final SnapshotRepository snapshotRepository;
    private final PlatformPersistenceMapper mapper;
    private final SensitiveDataFilter sensitiveDataFilter;
    private final ObjectMapper objectMapper;

    @Inject
    public SnapshotService(
            TargetResolver targetResolver,
            TargetAuthorizationService targetAuthorization,
            ServerInfoService serverInfoService,
            SnapshotRepository snapshotRepository,
            PlatformPersistenceMapper mapper,
            SensitiveDataFilter sensitiveDataFilter,
            ObjectMapper objectMapper) {
        this.targetResolver = targetResolver;
        this.targetAuthorization = targetAuthorization;
        this.serverInfoService = serverInfoService;
        this.snapshotRepository = snapshotRepository;
        this.mapper = mapper;
        this.sensitiveDataFilter = sensitiveDataFilter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SnapshotSummary create(String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("targetId", target.id().value());
        summary.put("displayName", target.displayName());
        summary.put("productType", target.type().name());
        summary.put("environment", target.environment().name());
        summary.put("keycloakUrl", target.keycloak().url());
        summary.put("authRealm", target.keycloak().authRealm());
        summary.put("infraType", target.infrastructureTypeOrNone().name());
        summary.put("tags", target.tags());

        try {
            ServerInfo info = serverInfoService.getServerInfo(targetId);
            summary.put("serverProduct", info.product() == null ? null : info.product().name());
            summary.put("serverVersion", info.version());
        } catch (RuntimeException e) {
            summary.put("serverInfoError", e.getMessage());
        }

        Map<String, Object> redacted = sensitiveDataFilter.redact(summary);
        String hash = sha256(normalizeJson(redacted));

        String envId = UUID.randomUUID().toString();
        EnvironmentSnapshotEntity env = new EnvironmentSnapshotEntity();
        env.id = envId;
        env.targetId = targetId;
        env.snapshotHash = hash;
        env.summary = redacted;
        env.createdAt = Instant.now();

        InventorySnapshotEntity inventory = new InventorySnapshotEntity();
        inventory.id = UUID.randomUUID().toString();
        inventory.targetId = targetId;
        inventory.environmentSnapshotId = envId;
        inventory.inventoryType = "basic";
        inventory.summary = Map.of("note", "basic inventory placeholder");
        inventory.createdAt = Instant.now();

        snapshotRepository.persistWithInventory(env, inventory);
        return mapper.toSnapshotSummary(env);
    }

    public PageResult<SnapshotSummary> list(String targetId, int page, int size) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        var pageResult = snapshotRepository.listByTarget(targetId, page, size);
        List<SnapshotSummary> items = pageResult.items().stream().map(mapper::toSnapshotSummary).toList();
        return new PageResult<>(items, pageResult.page(), pageResult.size(), pageResult.total());
    }

    public SnapshotSummary get(String targetId, String snapshotId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        EnvironmentSnapshotEntity entity = snapshotRepository.findByIdForTarget(snapshotId, targetId)
                .orElseThrow(() -> McpException.invalidArgument("snapshot not found: " + snapshotId));
        return mapper.toSnapshotSummary(entity);
    }

    public Optional<SnapshotSummary> latest(String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        return snapshotRepository.findLatest(targetId).map(mapper::toSnapshotSummary);
    }

    public Optional<EnvironmentSnapshotEntity> findEntity(String targetId, String snapshotId) {
        return snapshotRepository.findByIdForTarget(snapshotId, targetId);
    }

    private String normalizeJson(Map<String, Object> summary) {
        try {
            // TreeMap for stable key ordering of top-level; nested maps may still vary
            return objectMapper.writeValueAsString(new TreeMap<>(summary));
        } catch (JsonProcessingException e) {
            throw McpException.internal("Failed to serialize snapshot summary", e);
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
