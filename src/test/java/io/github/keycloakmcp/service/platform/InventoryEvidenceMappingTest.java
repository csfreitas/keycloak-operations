package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.domain.inventory.*;
import io.github.keycloakmcp.discovery.*;
import io.github.keycloakmcp.target.InfrastructureType;

class InventoryEvidenceMappingTest {
    private static final String CANARY = "opaque-canary https://secret.invalid/?token=private bearer-private";
    private static final Instant OBSERVED = Instant.parse("2026-09-18T12:00:00Z");
    private final InventoryService service = new InventoryService(null, null, null, null);

    @Test
    void fullyObservedZeroAndFalseAreNotMissing() {
        var evidence = evidence(new Fixture());
        assertThat(evidence).containsEntry("infrastructure.collection.complete", true)
                .containsEntry("cluster.nodes.count", 0)
                .containsEntry("cluster.zones.count", 0)
                .containsEntry("keycloak.pods.total", 0)
                .containsEntry("keycloak.pods.restartCount", 0L)
                .containsEntry("keycloak.pods.oomKilledCount", 0L)
                .containsEntry("deployment.replicas", 0)
                .containsEntry("keycloak.replicas.readyBelowDesired", false)
                .containsEntry("keycloak.hpa.present", false)
                .containsEntry("keycloak.pdb.present", false)
                .containsEntry("keycloak.route.present", false)
                .containsEntry("keycloak.probes.readiness.present", false)
                .containsEntry("keycloak.resources.requests.cpu.present", false);
    }

    @Test
    void nullPodsDoNotBecomeObservedZeroOrHealthyTopology() {
        var fixture = new Fixture();
        fixture.pods = null;
        var evidence = evidence(fixture);
        assertThat(evidence).containsEntry("infrastructure.collection.complete", false)
                .containsEntry("deployment.replicas", 0);
        assertThat(evidence.keySet()).noneMatch(k -> k.startsWith("keycloak.pods.") || k.startsWith("keycloak.topology."));
    }

    @Test
    void nullPodItemDoesNotThrowOrInventCounts() {
        var fixture = new Fixture();
        fixture.pods = Arrays.asList((PodInventoryItem) null);
        assertThat(evidence(fixture)).containsEntry("infrastructure.collection.complete", false)
                .doesNotContainKeys("keycloak.pods.total", "keycloak.topology.singleNodeConcentration");
    }

    @Test
    void unknownWorkloadSuppressesLegacyFalseDefaultsButRetainsKnownCluster() {
        var fixture = new Fixture();
        fixture.workload = KeycloakWorkloadInfo.unknown("ns");
        var evidence = evidence(fixture);
        assertThat(evidence).containsEntry("cluster.nodes.count", 0).containsEntry("infrastructure.collection.complete", false);
        assertThat(evidence.keySet()).noneMatch(k -> k.startsWith("keycloak.") || k.startsWith("deployment."));
    }

    @Test
    void nullDeploymentMethodIsNotEvidenceOfAValidWorkload() {
        var fixture = new Fixture();
        fixture.workload = new KeycloakWorkloadInfo(null, "ns", "kc", 3, 3, 3, 3);
        assertThat(evidence(fixture)).containsEntry("infrastructure.collection.complete", false)
                .doesNotContainKeys("deployment.replicas", "keycloak.hpa.present");
    }

    @Test
    void missingRequiredSectionsDoNotGainBooleanDefaults() {
        var fixture = new Fixture();
        fixture.hpa = null;
        fixture.pdb = null;
        fixture.resources = null;
        fixture.probes = null;
        fixture.scheduling = null;
        fixture.networking = null;
        assertThat(evidence(fixture)).containsEntry("infrastructure.collection.complete", false)
                .doesNotContainKeys("keycloak.hpa.present", "keycloak.pdb.present", "keycloak.route.present",
                        "keycloak.resources.requests.cpu.present", "keycloak.probes.readiness.present",
                        "keycloak.scheduling.zoneSpread.present");
    }

    @Test
    void deniedNodesDoNotEraseKnownPodPlacementButCannotProveZoneConcentration() {
        var fixture = placedPods();
        fixture.warnings = List.of(CollectionWarning.permissionDenied("nodes", CANARY));
        assertThat(evidence(fixture)).containsEntry("infrastructure.collection.complete", false)
                .containsEntry("keycloak.topology.zoneCount", 1)
                .containsEntry("keycloak.topology.singleNodeConcentration", true)
                .doesNotContainKeys("cluster.nodes.count", "cluster.zones.count", "keycloak.topology.singleZoneConcentration");
    }

    @Test
    void unavailablePodZonesPreserveKnownNodePlacement() {
        var fixture = placedPods();
        fixture.warnings = List.of(CollectionWarning.collectionFailed("pod-zones", CANARY));
        assertThat(evidence(fixture)).containsEntry("infrastructure.collection.complete", false)
                .containsEntry("keycloak.topology.singleNodeConcentration", true)
                .doesNotContainKeys("keycloak.topology.zoneCount", "keycloak.topology.podsByZone",
                        "keycloak.topology.singleZoneConcentration");
    }

    @Test
    void multizoneClusterWithPodsInOneZoneIsConcentrated() {
        var fixture = placedPods();
        assertThat(evidence(fixture)).containsEntry("keycloak.topology.singleZoneConcentration", true)
                .containsEntry("infrastructure.collection.complete", true);
    }

    @Test
    void partialZoneAndNodeMapsAreNotHealthySpread() {
        var fixture = placedPods();
        fixture.pods = List.of(new PodInventoryItem("a", "node", "zone", true, 0, false),
                new PodInventoryItem("b", null, null, true, 0, false));
        fixture.topology = new TopologyInfo(Map.of("zone", 1), Map.of("node", 1), 1);
        assertThat(evidence(fixture)).containsEntry("infrastructure.collection.complete", false)
                .containsEntry("keycloak.pods.total", 2)
                .doesNotContainKeys("keycloak.topology.zoneCount", "keycloak.topology.podsByNode",
                        "keycloak.topology.singleZoneConcentration", "keycloak.topology.singleNodeConcentration");
    }

    @Test
    void warningSuppressesOnlyItsDependentSection() {
        for (var resource : List.of("hpa", "pdb", "networking", "resources", "probes")) {
            var fixture = new Fixture();
            fixture.warnings = List.of(CollectionWarning.permissionDenied(resource, CANARY));
            var evidence = evidence(fixture);
            String key = switch (resource) {
                case "hpa" -> "keycloak.hpa.present";
                case "pdb" -> "keycloak.pdb.present";
                case "networking" -> "keycloak.route.present";
                case "resources" -> "keycloak.resources.requests.cpu.present";
                default -> "keycloak.probes.readiness.present";
            };
            assertThat(evidence).containsEntry("deployment.replicas", 0)
                    .containsEntry("infrastructure.collection.complete", false).doesNotContainKey(key);
        }
    }

    @Test
    void installationAndUnconfiguredWarningsOverrideLegacyDefaults() {
        var fixture = new Fixture();
        fixture.warnings = List.of(CollectionWarning.permissionDenied("installation", CANARY));
        assertThat(evidence(fixture)).containsEntry("cluster.nodes.count", 0)
                .doesNotContainKeys("deployment.replicas", "keycloak.pods.total", "keycloak.route.present");
        fixture.warnings = List.of(new CollectionWarning(CollectionWarning.WarningCode.NOT_CONFIGURED, "infrastructure", CANARY));
        assertThat(evidence(fixture)).containsEntry("infrastructure.collection.complete", false)
                .doesNotContainKeys("cluster.nodes.count", "deployment.replicas", "keycloak.hpa.present");
    }

    @Test
    void opaqueWarningsFailClosedRatherThanThrowing() {
        var fixture = new Fixture();
        for (var warnings : List.of(Arrays.asList((CollectionWarning) null),
                List.of(new CollectionWarning(null, null, CANARY)),
                List.of(new CollectionWarning(CollectionWarning.WarningCode.COLLECTION_FAILED, CANARY, CANARY)))) {
            fixture.warnings = warnings;
            var evidence = evidence(fixture);
            assertThat(evidence).containsEntry("collection.warning.unknown", "COLLECTION_FAILED")
                    .containsEntry("infrastructure.collection.complete", false);
            assertThat(evidence.keySet()).noneMatch(k -> k.startsWith("cluster.") || k.startsWith("keycloak."));
        }
    }

    @Test
    void missingWarningListCannotClaimCompleteCollection() {
        var fixture = new Fixture();
        fixture.warnings = null;
        assertThat(evidence(fixture)).containsEntry("infrastructure.collection.complete", false)
                .containsEntry("keycloak.hpa.present", false);
    }

    @Test
    void unknownHpaBoundsAreOmittedNotNegativeValues() {
        var fixture = new Fixture();
        fixture.hpa = new HpaInfo(true, -1, -1, -1);
        assertThat(evidence(fixture)).containsEntry("keycloak.hpa.present", true)
                .containsEntry("infrastructure.collection.complete", false)
                .doesNotContainKeys("keycloak.hpa.minReplicas", "keycloak.hpa.maxReplicas");
    }

    @Test
    void operatorReplicaParserRejectsFractionsNonfiniteOverflowAndNonNumbers() {
        var resource = new io.fabric8.kubernetes.api.model.GenericKubernetesResource();
        for (Object value : List.of(-1, 2.5, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                4_294_967_299L, new java.math.BigDecimal("2.00000000000000000000000000001"), "3")) {
            resource.setAdditionalProperty("spec", Map.of("instances", value));
            assertThat(InventoryService.readCrInstances(resource)).as("invalid value %s", value).isEqualTo(-1);
        }
        for (int value : List.of(0, 3, Integer.MAX_VALUE)) {
            resource.setAdditionalProperty("spec", Map.of("instances", value));
            assertThat(InventoryService.readCrInstances(resource)).isEqualTo(value);
        }
    }

    @Test
    void unknownPodRestartsAreOmittedAndKnownSumsDoNotOverflow() {
        var fixture = placedPods();
        fixture.pods = List.of(new PodInventoryItem("a", "node", "zone", true, -1, false));
        assertThat(evidence(fixture)).containsEntry("infrastructure.collection.complete", false)
                .doesNotContainKey("keycloak.pods.restartCount");
        fixture.pods = List.of(new PodInventoryItem("a", "node", "zone", true, Integer.MAX_VALUE, false),
                new PodInventoryItem("b", "node", "zone", true, Integer.MAX_VALUE, false));
        assertThat(evidence(fixture)).containsEntry("keycloak.pods.restartCount", 2L * Integer.MAX_VALUE);
    }

    @Test
    void incompleteNetworkObjectCannotProveRouteAbsence() {
        var fixture = new Fixture();
        fixture.networking = new NetworkingInfo(false, null, false, List.of(), List.of(), false);
        assertThat(evidence(fixture)).containsEntry("infrastructure.collection.complete", false)
                .doesNotContainKey("keycloak.route.present");
    }

    @Test
    void warningsDropAllProviderTextEvenWhenDeserializingLegacyPayload() throws Exception {
        for (var code : CollectionWarning.WarningCode.values()) {
            var warning = new CollectionWarning(code, "pods", CANARY);
            assertThat(new ObjectMapper().writeValueAsString(warning)).doesNotContain(CANARY);
            assertThat(warning.code()).isEqualTo(code);
            assertThat(warning.resource()).isEqualTo("pods");
        }
        var warning = new ObjectMapper().readValue("{\"code\":\"API_UNAVAILABLE\",\"resource\":\"pods\",\"message\":\"" + CANARY + "\"}", CollectionWarning.class);
        assertThat(warning.message()).isEqualTo("Resource API is unavailable");
    }

    @Test
    void evidenceKeepsTargetAndObservationTimeWithoutMutatingInventory() {
        var fixture = new Fixture();
        var inventory = fixture.build();
        var before = inventory.toString();
        assertThat(service.toEvidence(inventory)).allSatisfy(e -> {
            assertThat(e.targetId()).isEqualTo("target-a");
            assertThat(e.collectedAt()).isEqualTo(OBSERVED);
            assertThat(e.source()).isEqualTo("infrastructure");
        });
        assertThat(inventory.toString()).isEqualTo(before);
    }

    private Map<String, Object> evidence(Fixture fixture) {
        return service.toEvidence(fixture.build()).stream().collect(Collectors.toMap(Evidence::key, Evidence::value));
    }

    private static Fixture placedPods() {
        var fixture = new Fixture();
        fixture.cluster = new ClusterInfo("kubernetes", "v1.29.0", null, 3, 2);
        fixture.pods = List.of(new PodInventoryItem("a", "node", "zone", true, 0, false),
                new PodInventoryItem("b", "node", "zone", true, 0, false));
        fixture.topology = new TopologyInfo(Map.of("zone", 2), Map.of("node", 2), 1);
        return fixture;
    }

    private static final class Fixture {
        ClusterInfo cluster = new ClusterInfo("kubernetes", "v1.29.0", null, 0, 0);
        KeycloakWorkloadInfo workload = new KeycloakWorkloadInfo(DeploymentMethod.DEPLOYMENT, "ns", "kc", 0, 0, 0, 0,
                "apps/v1", "Deployment", "uid");
        List<PodInventoryItem> pods = List.of();
        TopologyInfo topology = new TopologyInfo(Map.of(), Map.of(), 0);
        SchedulingInfo scheduling = new SchedulingInfo(false, false);
        HpaInfo hpa = HpaInfo.absent();
        PdbInfo pdb = PdbInfo.absent();
        ResourceConfig resources = new ResourceConfig(null, null, null, null);
        ProbeInfo probes = new ProbeInfo(false, false, false);
        NetworkingInfo networking = new NetworkingInfo(false, null, false);
        List<CollectionWarning> warnings = List.of();

        InfrastructureInventory build() {
            return new InfrastructureInventory("target-a", "KUBERNETES", cluster, workload, pods, topology,
                    scheduling, hpa, pdb, resources, probes, networking, warnings, OBSERVED,
                    new EnvironmentInfo(RuntimeType.KUBERNETES, DetectionConfidence.CONFIRMED,
                            "kubernetes", "ns", List.of(), "target-a", "v1.29.0", "kubernetes",
                            InfrastructureType.KUBERNETES, new ClusterApiCapabilities(
                                    ClusterApiCapabilities.ApiAvailability.NOT_SERVED,
                                    ClusterApiCapabilities.ApiAvailability.NOT_SERVED)));
        }
    }
}
