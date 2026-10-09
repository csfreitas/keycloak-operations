package io.github.keycloakmcp.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.adapter.infrastructure.InfrastructureClientFactory;
import io.github.keycloakmcp.config.HealthConfig;
import io.github.keycloakmcp.domain.inventory.*;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.service.platform.InventoryService;
import io.github.keycloakmcp.target.*;

class InventoryHealthChecksTest {
    private static final String CANARY = "untrusted-provider-secret-canary";
    private static final Target TARGET = new Target(TargetId.of("t1"), "Test", TargetType.KEYCLOAK,
            TargetEnvironment.DEV, true, new KeycloakTargetConfiguration("http://localhost", "master", "client", "ref"),
            new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "cluster", "ns", "cred"), null, Map.of());
    private InventoryService inventory;
    private InfrastructureClientFactory factory;
    private PodHealthCheck pods;
    private WorkloadHealthCheck workload;
    private InfrastructureApiHealthCheck api;

    @BeforeEach
    void setUp() {
        inventory = mock(InventoryService.class);
        factory = mock(InfrastructureClientFactory.class);
        when(factory.resolve(TARGET)).thenReturn(Optional.of(mock(ClusterClient.class)));
        HealthConfig config = mock(HealthConfig.class, RETURNS_DEEP_STUBS);
        when(config.pods().restartWarningThreshold()).thenReturn(3);
        pods = new PodHealthCheck(inventory, config);
        workload = new WorkloadHealthCheck(inventory);
        api = new InfrastructureApiHealthCheck(inventory, factory);
    }

    @Test
    void providerExceptionsAreUnknownAndDoNotExposeMessagesOrCauses() {
        when(inventory.collect("t1")).thenThrow(new IllegalStateException(CANARY, new IllegalArgumentException(CANARY)));
        for (HealthCheck check : List.of(pods, workload, api)) {
            var result = check.check(TARGET);
            assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
            assertThat(result.details()).containsEntry("reasonCode", "CHECK_FAILED");
            assertThat(result.toString()).doesNotContain(CANARY);
        }
    }

    @Test
    void unboundInventoryCannotBeHealthyOrFabricateZeroCounts() {
        when(inventory.collect("t1")).thenReturn(data(KeycloakWorkloadInfo.unknown("ns"), List.of(),
                List.of(new CollectionWarning(CollectionWarning.WarningCode.BINDING_REQUIRED, "installation", CANARY))));
        for (HealthCheck check : List.of(pods, workload, api)) {
            var result = check.check(TARGET);
            assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
            assertThat(result.toString()).doesNotContain(CANARY);
            assertThat(result.details()).doesNotContainKeys("podCount", "readyPods", "desiredReplicas");
        }
    }

    @Test
    void deniedPodsAreUnknownNotAnOutageEvenWithPositiveDesiredCount() {
        when(inventory.collect("t1")).thenReturn(data(known(2, 2), List.of(),
                List.of(CollectionWarning.permissionDenied("pods", CANARY))));
        var result = pods.check(TARGET);
        assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(result.details()).doesNotContainKey("podCount");
        assertThat(result.toString()).doesNotContain(CANARY);
        assertThat(workload.check(TARGET).status()).isEqualTo(HealthStatus.HEALTHY);
    }

    @Test
    void missingInventoryIsUnknownForEveryCheck() {
        when(inventory.collect("t1")).thenReturn(null);
        for (HealthCheck check : List.of(pods, workload, api)) {
            assertThat(check.check(TARGET).status()).isEqualTo(HealthStatus.UNKNOWN);
        }
    }

    @Test
    void missingWorkloadOrUnknownReplicasIsUnknownWithoutNullMapFailure() {
        for (KeycloakWorkloadInfo info : Arrays.asList(null, KeycloakWorkloadInfo.unknown("ns"), known(-1, -1))) {
            when(inventory.collect("t1")).thenReturn(data(info, List.of(), List.of()));
            assertThat(workload.check(TARGET).status()).isEqualTo(HealthStatus.UNKNOWN);
            assertThat(pods.check(TARGET).status()).isEqualTo(HealthStatus.UNKNOWN);
        }
    }

    @Test
    void nullPodCollectionAndMalformedPodAreUnknown() {
        for (List<PodInventoryItem> items : Arrays.<List<PodInventoryItem>>asList(null,
                Arrays.asList((PodInventoryItem) null), List.of(new PodInventoryItem("pod", "node", "zone", true, -1, false)))) {
            when(inventory.collect("t1")).thenReturn(data(known(1, 1), items, List.of()));
            assertThat(pods.check(TARGET).status()).isEqualTo(HealthStatus.UNKNOWN);
        }
    }

    @Test
    void observedEmptyPodsWithDesiredReplicasStillCritical() {
        when(inventory.collect("t1")).thenReturn(data(known(1, 0), List.of(), List.of()));
        assertThat(pods.check(TARGET).status()).isEqualTo(HealthStatus.CRITICAL);
        assertThat(workload.check(TARGET).status()).isEqualTo(HealthStatus.CRITICAL);
    }

    @Test
    void observedOomRemainsCriticalAndRestartThresholdRemainsWarning() {
        when(inventory.collect("t1")).thenReturn(data(known(1, 1),
                List.of(new PodInventoryItem("pod", "node", "zone", true, 0, true)), List.of()));
        assertThat(pods.check(TARGET).status()).isEqualTo(HealthStatus.CRITICAL);
        when(inventory.collect("t1")).thenReturn(data(known(1, 1),
                List.of(new PodInventoryItem("pod", "node", "zone", true, 3, false)), List.of()));
        assertThat(pods.check(TARGET).status()).isEqualTo(HealthStatus.WARNING);
    }

    @Test
    void unrelatedNetworkWarningsDoNotEraseObservedWorkloadAndPodHealth() {
        when(inventory.collect("t1")).thenReturn(data(known(1, 1),
                List.of(new PodInventoryItem("pod", "node", "zone", true, 0, false)),
                List.of(CollectionWarning.collectionFailed("networking", CANARY))));
        assertThat(pods.check(TARGET).status()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(workload.check(TARGET).status()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(api.check(TARGET).status()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(api.check(TARGET).toString()).doesNotContain(CANARY);
    }

    @Test
    void opaqueWarningPreventsFavorableComponentResult() {
        when(inventory.collect("t1")).thenReturn(data(known(1, 1), List.of(),
                List.of(new CollectionWarning(CollectionWarning.WarningCode.COLLECTION_FAILED, null, CANARY))));
        assertThat(pods.check(TARGET).status()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(workload.check(TARGET).status()).isEqualTo(HealthStatus.UNKNOWN);
    }

    @Test
    void unresolvedOrFailingClientIsUnknownWithoutCallingInventory() {
        when(factory.resolve(TARGET)).thenReturn(Optional.empty());
        assertThat(api.check(TARGET).status()).isEqualTo(HealthStatus.UNKNOWN);
        when(factory.resolve(TARGET)).thenThrow(new IllegalArgumentException(CANARY));
        var result = api.check(TARGET);
        assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(result.toString()).doesNotContain(CANARY);
        verifyNoInteractions(inventory);
    }

    @Test
    void explicitlyObservedScaledToZeroIsNotMissingEvidence() {
        when(inventory.collect("t1")).thenReturn(InfrastructureInventoryFixtures.scaledToZero("t1"));
        assertThat(workload.check(TARGET).status()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(pods.check(TARGET).status()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(api.check(TARGET).status()).isEqualTo(HealthStatus.HEALTHY);
    }

    @Test
    void warningFreeUnknownRuntimeAndMissingVersionDoNotEstablishCompleteApiCoverage() {
        for (var observation : List.of(InfrastructureInventoryFixtures.withoutRuntimeObservation("t1"),
                InfrastructureInventoryFixtures.withoutVersionObservation("t1"))) {
            when(inventory.collect("t1")).thenReturn(observation);
            var result = api.check(TARGET);
            assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
            assertThat(result.details()).containsEntry("collectionComplete", false)
                    .containsEntry("reasonCode", "EVIDENCE_INCOMPLETE").containsEntry("warningCount", 0);
            assertThat(workload.check(TARGET).status()).isEqualTo(HealthStatus.HEALTHY);
            assertThat(pods.check(TARGET).status()).isEqualTo(HealthStatus.HEALTHY);
        }
    }

    @Test
    void completeObservedInventoryRecordsApiCoverage() {
        when(inventory.collect("t1")).thenReturn(InfrastructureInventoryFixtures.complete("t1"));
        var result = api.check(TARGET);
        assertThat(result.status()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(result.details()).containsEntry("collectionComplete", true).containsEntry("runtime", "KUBERNETES");
    }

    @Test
    void foreignCompleteInventoryCannotEstablishHealthOrExposeItsRuntime() {
        when(inventory.collect("t1")).thenReturn(InfrastructureInventoryFixtures.complete("foreign-target"));
        var result = api.check(TARGET);
        assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(result.details()).containsEntry("collectionComplete", false)
                .containsEntry("reasonCode", "EVIDENCE_SCOPE_MISMATCH").doesNotContainKey("runtime");
        assertThat(result.toString()).doesNotContain("foreign-target");
    }

    private static KeycloakWorkloadInfo known(int desired, int ready) {
        return new KeycloakWorkloadInfo(DeploymentMethod.DEPLOYMENT, "ns", "sso", desired, ready, ready, ready);
    }

    private static InfrastructureInventory data(KeycloakWorkloadInfo info, List<PodInventoryItem> pods,
            List<CollectionWarning> warnings) {
        return new InfrastructureInventory("t1", "KUBERNETES", null, info, pods, null, null, null, null,
                null, null, null, warnings, Instant.EPOCH);
    }
}
