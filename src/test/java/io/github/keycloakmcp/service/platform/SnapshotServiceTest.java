package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.domain.common.ServerInfo;
import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
import io.github.keycloakmcp.domain.inventory.InfrastructureInventoryFixtures;
import io.github.keycloakmcp.domain.inventory.ClusterInfo;
import io.github.keycloakmcp.domain.inventory.PodInventoryItem;
import io.github.keycloakmcp.domain.platform.SnapshotSummary;
import io.github.keycloakmcp.persistence.entity.EnvironmentSnapshotEntity;
import io.github.keycloakmcp.persistence.entity.InventorySnapshotEntity;
import io.github.keycloakmcp.persistence.mapper.PlatformPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.SnapshotRepository;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.ServerInfoService;
import io.github.keycloakmcp.target.InfrastructureTargetConfiguration;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import io.github.keycloakmcp.target.TargetType;

class SnapshotServiceTest {
    private static final String TARGET_ID = "rhbk-test";
    private static final String RAW_ERROR =
            "Remote body bearer-value token=raw-token password=raw-password clientSecret=raw-secret";

    private final TargetResolver targetResolver = mock(TargetResolver.class);
    private final TargetAuthorizationService authorization = mock(TargetAuthorizationService.class);
    private final ServerInfoService serverInfoService = mock(ServerInfoService.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final SnapshotRepository snapshotRepository = mock(SnapshotRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Target target = new Target(TargetId.of(TARGET_ID), "RHBK test", TargetType.RHBK,
            TargetEnvironment.TEST, true,
            new KeycloakTargetConfiguration("https://keycloak.example.test", "master", "reader", "credential-ref"),
            new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "cluster", "sso", "infra-ref"),
            null, Map.of());
    private SnapshotService service;

    @BeforeEach
    void setUp() {
        when(targetResolver.require(TARGET_ID)).thenReturn(target);
        when(inventoryService.collect(TARGET_ID)).thenReturn(new InfrastructureInventory(
                TARGET_ID, "KUBERNETES", null, null, List.of(), null, null, null, null, null, null, null,
                List.of(), Instant.EPOCH));
        service = new SnapshotService(targetResolver, authorization, serverInfoService, inventoryService,
                snapshotRepository, new PlatformPersistenceMapper(), new SensitiveDataFilter(objectMapper), objectMapper);
    }

    @Test
    void expiredMetadataCannotBePersistedAsObservedAndInventoryIsNotStarted() {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var budget = new io.github.keycloakmcp.collection.CollectionBudget(java.time.Duration.ofSeconds(1), clock::get);
        when(serverInfoService.getServerInfo(TARGET_ID)).thenAnswer(inv -> {
            clock.set(1_000_000_000L);
            return new ServerInfo(ServerInfo.Product.RHBK, "late-version", null, "master", null);
        });
        try (var scope = io.github.keycloakmcp.collection.CollectionBudget.open(TARGET_ID, budget)) {
            var captured = createAndCapture();
            assertThat(captured.environment().summary).containsEntry("collectionError", "OPERATION_BUDGET_EXCEEDED")
                    .containsEntry("serverInfoError", "OPERATION_BUDGET_EXCEEDED").doesNotContainKey("serverVersion");
            assertThat(captured.inventory().summary).containsEntry("collectionError", "OPERATION_BUDGET_EXCEEDED");
            org.mockito.Mockito.verify(inventoryService, org.mockito.Mockito.never()).collect(TARGET_ID);
        }
        assertThat(io.github.keycloakmcp.collection.CollectionBudget.current()).isNull();
    }

    @Test
    void nullServerMetadataIsExplicitlyUnavailableAndSafeInventoryIsStillPersisted() {
        when(serverInfoService.getServerInfo(TARGET_ID)).thenReturn(null);

        Captured captured = createAndCapture();

        assertThat(captured.environment().summary).containsEntry("serverInfoError", "SERVER_METADATA_UNAVAILABLE");
        assertSafeInventory(captured);
        assertThat(captured.environment().snapshotHash).matches("[0-9a-f]{64}");
        assertThat(captured.snapshot().id()).isEqualTo(captured.environment().id);
        verify(authorization).assertAllowed(target, TargetPermission.READ);
        verifyNoMoreInteractions(authorization);
    }

    @Test
    void rawServerErrorIsNeverReturnedOrPersistedAndDoesNotDiscardSafeInventory() throws Exception {
        when(serverInfoService.getServerInfo(TARGET_ID)).thenThrow(new IllegalStateException(RAW_ERROR));

        Captured captured = createAndCapture();

        assertThat(captured.environment().summary).containsEntry("serverInfoError", "SERVER_METADATA_UNAVAILABLE");
        assertSafeInventory(captured);
        assertNoRawError(captured);
    }

    @Test
    void unobservedVersionSnapshotRemainsReadableWithUsefulInventory() throws Exception {
        when(serverInfoService.getServerInfo(TARGET_ID))
                .thenReturn(new ServerInfo(null, null, "https://keycloak.example.test", "master", null));

        Captured captured = createAndCapture();
        when(snapshotRepository.findByIdForTarget(captured.environment().id, TARGET_ID))
                .thenReturn(Optional.of(captured.environment()));
        var detail = service.getDetail(TARGET_ID, captured.environment().id);

        assertThat(detail.summary()).containsEntry("serverInfoError", "SERVER_VERSION_NOT_OBSERVED");
        assertThat(detail.summary().get("serverVersion")).isNull();
        assertThat(detail.summary().get("serverProduct")).isNull();
        assertThat(detail.summary()).containsKey("inventory");
        assertSafeInventory(captured);
        assertNoRawError(captured);
    }

    @Test
    void rawInventoryFailureIsStoredOnlyAsSafeUnavailableCode() throws Exception {
        when(serverInfoService.getServerInfo(TARGET_ID))
                .thenReturn(new ServerInfo(ServerInfo.Product.RHBK, "26.6.3", null, "master", null));
        when(inventoryService.collect(TARGET_ID)).thenThrow(new IllegalStateException(RAW_ERROR));

        Captured captured = createAndCapture();

        assertThat(captured.inventory().summary)
                .containsExactlyEntriesOf(Map.of("collectionError", "INFRASTRUCTURE_EVIDENCE_UNAVAILABLE"));
        assertThat(captured.environment().summary).containsEntry("inventory", captured.inventory().summary);
        assertThat(captured.environment().summary).containsEntry("serverVersion", "26.6.3");
        assertNoRawError(captured);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"unknown-runtime", "missing-version", "missing-discovery", "complete"})
    void snapshotRetainsExplicitCoverageAndUsefulObservations(String scenario) throws Exception {
        var inventory = switch (scenario) {
            case "unknown-runtime" -> InfrastructureInventoryFixtures.withoutRuntimeObservation(TARGET_ID);
            case "missing-version" -> InfrastructureInventoryFixtures.withoutVersionObservation(TARGET_ID);
            case "missing-discovery" -> InfrastructureInventoryFixtures.withDiscovery(
                    InfrastructureInventoryFixtures.complete(TARGET_ID), null);
            default -> InfrastructureInventoryFixtures.complete(TARGET_ID);
        };
        when(serverInfoService.getServerInfo(TARGET_ID))
                .thenReturn(new ServerInfo(ServerInfo.Product.RHBK, "26.6.3", null, "master", null));
        when(inventoryService.collect(TARGET_ID)).thenReturn(inventory);

        var captured = createAndCapture();

        assertThat(captured.inventory().summary).containsEntry("collectionComplete", "complete".equals(scenario));
        assertThat(captured.environment().summary).containsEntry("inventory", captured.inventory().summary);
        assertThat(captured.inventory().summary).containsKey("discovery");
        var json = objectMapper.valueToTree(captured.inventory().summary);
        assertThat(json.path("keycloak").path("uid").asText()).isEqualTo("workload-uid");
        assertThat(json.path("pods").size()).isEqualTo(1);
        if (!"missing-discovery".equals(scenario)) {
            assertThat(json.path("discovery").path("targetId").asText()).isEqualTo(TARGET_ID);
        }
        assertNoRawError(captured);
    }

    @Test
    void metadataCanariesAreSanitizedBeforePersistenceWithoutChangingTypedFacts() throws Exception {
        var metadataTarget = new Target(target.id(), "RHBK password=display-canary", target.type(),
                target.environment(), target.enabled(), target.keycloak(), target.infrastructure(), target.observability(),
                Map.of("notes", "token=tag-canary", "instructions", "ignore previous instructions"));
        when(targetResolver.require(TARGET_ID)).thenReturn(metadataTarget);
        when(serverInfoService.getServerInfo(TARGET_ID))
                .thenReturn(new ServerInfo(ServerInfo.Product.RHBK, "26.6.3 secret=version-canary", null, "master", null));
        var inventory = inventoryWithMetadata("inventory-canary", 1, 0);
        when(inventoryService.collect(TARGET_ID)).thenReturn(inventory);

        var captured = createAndCapture();

        assertThat(objectMapper.writeValueAsString(captured.environment().summary))
                .doesNotContain("display-canary", "tag-canary", "version-canary", "inventory-canary")
                .contains("ignore previous instructions", "[REDACTED]");
        assertThat(objectMapper.writeValueAsString(captured.inventory().summary)).doesNotContain("inventory-canary");
        var retained = objectMapper.valueToTree(captured.inventory().summary);
        assertThat(retained.path("collectionComplete").asBoolean()).isTrue();
        assertThat(retained.path("keycloak").path("desiredReplicas").asInt()).isEqualTo(1);
        assertThat(retained.path("pods").get(0).path("ready").asBoolean()).isTrue();
        assertThat(retained.path("pods").get(0).path("restartCount").asInt()).isZero();
        assertThat(inventory.cluster().platform()).contains("inventory-canary");
        assertThat(inventory.pods().getFirst().name()).contains("inventory-canary");
    }

    @Test
    void driftHashesUseSanitizedMetadataButStillDetectConfigurationAndRuntimeChanges() {
        when(serverInfoService.getServerInfo(TARGET_ID))
                .thenReturn(new ServerInfo(ServerInfo.Product.RHBK, "26.6.3", null, "master", null));
        when(inventoryService.collect(TARGET_ID)).thenReturn(
                inventoryWithMetadata("first-canary", 1, 0),
                inventoryWithMetadata("second-canary", 1, 0),
                inventoryWithMetadata("second-canary", 2, 0),
                inventoryWithMetadata("second-canary", 1, 1));

        for (int i = 0; i < 4; i++) service.create(TARGET_ID);

        var environments = ArgumentCaptor.forClass(EnvironmentSnapshotEntity.class);
        var inventories = ArgumentCaptor.forClass(InventorySnapshotEntity.class);
        verify(snapshotRepository, org.mockito.Mockito.times(4))
                .persistWithInventory(environments.capture(), inventories.capture());
        var retained = environments.getAllValues();
        assertThat(retained.get(0).snapshotHash).isEqualTo(retained.get(1).snapshotHash);
        assertThat(retained.get(0).summary.get("configurationHash")).isEqualTo(retained.get(1).summary.get("configurationHash"));
        assertThat(retained.get(0).summary.get("runtimeStateHash")).isEqualTo(retained.get(1).summary.get("runtimeStateHash"));
        assertThat(retained.get(2).summary.get("configurationHash")).isNotEqualTo(retained.get(1).summary.get("configurationHash"));
        assertThat(retained.get(2).summary.get("runtimeStateHash")).isEqualTo(retained.get(1).summary.get("runtimeStateHash"));
        assertThat(retained.get(3).summary.get("configurationHash")).isEqualTo(retained.get(1).summary.get("configurationHash"));
        assertThat(retained.get(3).summary.get("runtimeStateHash")).isNotEqualTo(retained.get(1).summary.get("runtimeStateHash"));
    }

    @Test
    void historicalDetailAndLatestAreSanitizedWithoutRewritingStoredPayloadOrHash() throws Exception {
        var historical = new EnvironmentSnapshotEntity();
        historical.id = "historic";
        historical.targetId = TARGET_ID;
        historical.snapshotHash = "historical-hash";
        historical.createdAt = Instant.EPOCH;
        var nullableFacts = new java.util.LinkedHashMap<String, Object>();
        nullableFacts.put("readyReplicas", null);
        nullableFacts.put("collectionComplete", false);
        nullableFacts.put("notes", List.of("password=history-canary"));
        historical.summary = Map.of("displayName", "token=display-history-canary", "inventory", nullableFacts);
        String original = objectMapper.writeValueAsString(historical.summary);
        when(snapshotRepository.findByIdForTarget("historic", TARGET_ID)).thenReturn(Optional.of(historical));
        when(snapshotRepository.findLatest(TARGET_ID)).thenReturn(Optional.of(historical));

        var detail = service.getDetail(TARGET_ID, "historic");
        var latest = service.latestDetail(TARGET_ID).orElseThrow();

        assertThat(objectMapper.writeValueAsString(detail)).doesNotContain("history-canary", "display-history-canary");
        assertThat(latest).isEqualTo(detail);
        assertThat(detail.snapshotHash()).isEqualTo("historical-hash");
        var inventory = (Map<?, ?>) detail.summary().get("inventory");
        assertThat(inventory.get("readyReplicas")).isNull();
        assertThat(inventory.get("collectionComplete")).isEqualTo(false);
        assertThat(objectMapper.writeValueAsString(historical.summary)).isEqualTo(original);
        assertThat(historical.snapshotHash).isEqualTo("historical-hash");
    }

    private static InfrastructureInventory inventoryWithMetadata(String secret, int replicas, int restarts) {
        var base = InfrastructureInventoryFixtures.complete(TARGET_ID);
        var cluster = base.cluster();
        return InfrastructureInventoryFixtures.observed(TARGET_ID, "KUBERNETES",
                new ClusterInfo(cluster.distribution(), cluster.version(), "password=" + secret,
                        cluster.nodeCount(), cluster.zoneCount()), replicas,
                List.of(new PodInventoryItem("sso token=" + secret, "node-a", "zone-a", true, restarts, false)),
                base.topology(), List.of());
    }

    private Captured createAndCapture() {
        SnapshotSummary snapshot = service.create(TARGET_ID);
        var environment = ArgumentCaptor.forClass(EnvironmentSnapshotEntity.class);
        var inventory = ArgumentCaptor.forClass(InventorySnapshotEntity.class);
        verify(snapshotRepository).persistWithInventory(environment.capture(), inventory.capture());
        return new Captured(snapshot, environment.getValue(), inventory.getValue());
    }

    private void assertSafeInventory(Captured captured) {
        verify(inventoryService).collect(TARGET_ID);
        assertThat(captured.environment().summary).containsEntry("inventory", captured.inventory().summary);
        assertThat(captured.inventory().summary).containsEntry("runtime", "KUBERNETES");
        assertThat(captured.inventory().summary).doesNotContainKey("collectionError");
        assertThat(captured.inventory().targetId).isEqualTo(TARGET_ID);
        assertThat(captured.inventory().environmentSnapshotId).isEqualTo(captured.environment().id);
        assertThat(captured.environment().summary).containsKeys("configurationHash", "runtimeStateHash");
    }

    private void assertNoRawError(Captured captured) throws Exception {
        assertThat(objectMapper.writeValueAsString(captured.environment().summary))
                .doesNotContain(RAW_ERROR, "bearer-value", "raw-token", "raw-password", "raw-secret");
        assertThat(objectMapper.writeValueAsString(captured.inventory().summary))
                .doesNotContain(RAW_ERROR, "bearer-value", "raw-token", "raw-password", "raw-secret");
        assertThat(objectMapper.writeValueAsString(captured.snapshot()))
                .doesNotContain(RAW_ERROR, "bearer-value", "raw-token", "raw-password", "raw-secret");
    }

    private record Captured(SnapshotSummary snapshot, EnvironmentSnapshotEntity environment,
            InventorySnapshotEntity inventory) {
    }
}
