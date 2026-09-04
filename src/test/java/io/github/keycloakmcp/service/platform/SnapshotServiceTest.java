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
