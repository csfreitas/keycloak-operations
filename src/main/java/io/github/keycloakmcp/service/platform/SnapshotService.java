package io.github.keycloakmcp.service.platform;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
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
import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.domain.platform.SnapshotDetail;
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
    private final InventoryService inventoryService;
    private final SnapshotRepository snapshotRepository;
    private final PlatformPersistenceMapper mapper;
    private final SensitiveDataFilter sensitiveDataFilter;
    private final ObjectMapper objectMapper;

    @Inject
    public SnapshotService(
            TargetResolver targetResolver,
            TargetAuthorizationService targetAuthorization,
            ServerInfoService serverInfoService,
            InventoryService inventoryService,
            SnapshotRepository snapshotRepository,
            PlatformPersistenceMapper mapper,
            SensitiveDataFilter sensitiveDataFilter,
            ObjectMapper objectMapper) {
        this.targetResolver = targetResolver;
        this.targetAuthorization = targetAuthorization;
        this.serverInfoService = serverInfoService;
        this.inventoryService = inventoryService;
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

        Map<String, Object> inventorySummary = new LinkedHashMap<>();
        try {
            InfrastructureInventory inventory = inventoryService.collect(targetId);
            inventorySummary = toInventorySummary(inventory);
            summary.put("inventory", inventorySummary);
            summary.put("configurationHash", sha256(normalizeJson(configurationSlice(inventorySummary))));
            summary.put("runtimeStateHash", sha256(normalizeJson(runtimeSlice(inventorySummary))));
        } catch (RuntimeException e) {
            inventorySummary = Map.of(
                    "collectionError", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            summary.put("inventory", inventorySummary);
        }

        Map<String, Object> redacted = sensitiveDataFilter.redact(summary);
        Map<String, Object> redactedInventory = sensitiveDataFilter.redact(inventorySummary);
        String hash = sha256(normalizeJson(redacted));

        String envId = UUID.randomUUID().toString();
        EnvironmentSnapshotEntity env = new EnvironmentSnapshotEntity();
        env.id = envId;
        env.targetId = targetId;
        env.snapshotHash = hash;
        env.summary = redacted;
        env.createdAt = Instant.now();

        InventorySnapshotEntity inventoryEntity = new InventorySnapshotEntity();
        inventoryEntity.id = UUID.randomUUID().toString();
        inventoryEntity.targetId = targetId;
        inventoryEntity.environmentSnapshotId = envId;
        inventoryEntity.inventoryType = "infrastructure";
        inventoryEntity.summary = redactedInventory;
        inventoryEntity.createdAt = Instant.now();

        snapshotRepository.persistWithInventory(env, inventoryEntity);
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

    public SnapshotDetail getDetail(String targetId, String snapshotId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        EnvironmentSnapshotEntity entity = snapshotRepository.findByIdForTarget(snapshotId, targetId)
                .orElseThrow(() -> McpException.invalidArgument("snapshot not found: " + snapshotId));
        return toDetail(entity);
    }

    public Optional<SnapshotDetail> latestDetail(String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        return snapshotRepository.findLatest(targetId).map(this::toDetail);
    }

    public Optional<SnapshotSummary> latest(String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        return snapshotRepository.findLatest(targetId).map(mapper::toSnapshotSummary);
    }

    private SnapshotDetail toDetail(EnvironmentSnapshotEntity entity) {
        return new SnapshotDetail(
                entity.id,
                entity.targetId,
                entity.snapshotHash,
                entity.createdAt,
                entity.summary == null ? Map.of() : Map.copyOf(entity.summary));
    }

    public Optional<EnvironmentSnapshotEntity> findEntity(String targetId, String snapshotId) {
        return snapshotRepository.findByIdForTarget(snapshotId, targetId);
    }

    private Map<String, Object> toInventorySummary(InfrastructureInventory inventory) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("targetId", inventory.targetId());
        map.put("runtime", inventory.runtime());
        map.put("cluster", inventory.cluster());
        map.put("keycloak", inventory.keycloak());
        map.put("topology", inventory.topology());
        map.put("scheduling", inventory.scheduling());
        map.put("hpa", inventory.hpa());
        map.put("pdb", inventory.pdb());
        map.put("resources", inventory.resources());
        map.put("networking", inventory.networking());
        map.put("pods", inventory.pods());
        map.put("warnings", inventory.warnings());
        map.put("collectedAt", inventory.collectedAt() == null ? null : inventory.collectedAt().toString());
        return map;
    }

    private Map<String, Object> configurationSlice(Map<String, Object> inventory) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        for (String key : List.of("runtime", "cluster", "keycloak", "scheduling", "hpa", "pdb", "resources", "networking")) {
            if (inventory.containsKey(key)) {
                cfg.put(key, inventory.get(key));
            }
        }
        return cfg;
    }

    private Map<String, Object> runtimeSlice(Map<String, Object> inventory) {
        Map<String, Object> runtime = new LinkedHashMap<>();
        for (String key : List.of("pods", "topology", "warnings", "collectedAt")) {
            if (inventory.containsKey(key)) {
                runtime.put(key, inventory.get(key));
            }
        }
        return runtime;
    }

    @SuppressWarnings("unchecked")
    private String normalizeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(deepSort(value));
        } catch (JsonProcessingException e) {
            throw McpException.internal("Failed to serialize snapshot summary", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object deepSort(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                // Drop volatile Kubernetes fields that cause false drift
                if (key.equals("resourceVersion")
                        || key.equals("managedFields")
                        || key.equals("uid")
                        || key.equals("creationTimestamp")
                        || key.equals("collectedAt")) {
                    continue;
                }
                sorted.put(key, deepSort(e.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> sorted = new ArrayList<>(list.size());
            for (Object item : list) {
                sorted.add(deepSort(item));
            }
            return sorted;
        }
        return value;
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
