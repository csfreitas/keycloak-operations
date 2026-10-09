package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;
import org.junit.jupiter.api.Test;

class TargetOverviewSignalsTest {
    private Map<String, Object> inventory() {
        var inv = new HashMap<String, Object>();
        inv.put("runtime", "KUBERNETES");
        inv.put("keycloak", Map.of("name", "kc", "desiredReplicas", 0, "readyReplicas", 0));
        inv.put("pods", List.of());
        inv.put("topology", Map.of("zoneCount", 0));
        inv.put("warnings", List.of());
        return inv;
    }

    private TargetOverviewService.OverviewSignals read(Map<String, Object> inv) {
        return TargetOverviewService.OverviewSignals.from(Map.of("inventory", inv));
    }

    @Test void unconfiguredInfrastructureDoesNotBecomeZeroCapacity() {
        var result = TargetOverviewService.OverviewSignals.from(Map.of("infraType", "NONE", "inventory", inventory()));
        assertThat(result.desiredReplicas()).isNull();
        assertThat(result.readyReplicas()).isNull();
        assertThat(result.podCount()).isNull();
        assertThat(result.zoneCount()).isNull();
    }

    @Test void observedZerosRemainZeros() {
        var result = read(inventory());
        assertThat(result.desiredReplicas()).isZero();
        assertThat(result.readyReplicas()).isZero();
        assertThat(result.podCount()).isZero();
        assertThat(result.zoneCount()).isZero();
    }

    @Test void negativeAndMalformedReplicaCountsAreUnknown() {
        var inv = inventory();
        for (Object invalid : List.of(-1, "-1", "invalid", 1.5, Double.NaN, Long.MAX_VALUE)) {
            inv.put("keycloak", Map.of("name", "kc", "desiredReplicas", invalid, "readyReplicas", invalid));
            assertThat(read(inv).desiredReplicas()).isNull();
            assertThat(read(inv).readyReplicas()).isNull();
        }
    }

    @Test void missingBindingOrCollectionDoesNotBecomeZero() {
        for (String resource : List.of("infrastructure", "installation", "workload")) {
            var inv = inventory();
            inv.put("warnings", List.of(Map.of("resource", resource, "code", "COLLECTION_FAILED")));
            assertThat(read(inv).desiredReplicas()).isNull();
            assertThat(read(inv).podCount()).isNull();
            assertThat(read(inv).zoneCount()).isNull();
        }
        var inv = inventory();
        inv.put("keycloak", Map.of("desiredReplicas", -1));
        assertThat(read(inv).podCount()).isNull();
    }

    @Test void podDenialPreservesObservedReplicasButNotEmptyPodCount() {
        var inv = inventory();
        inv.put("warnings", List.of(Map.of("resource", "pods", "code", "PERMISSION_DENIED")));
        assertThat(read(inv).desiredReplicas()).isZero();
        assertThat(read(inv).podCount()).isNull();
        assertThat(read(inv).zoneCount()).isNull();
    }

    @Test void nodeDenialAndMissingZoneLabelsKeepZoneCountUnknown() {
        var inv = inventory();
        inv.put("pods", List.of(Map.of("name", "kc-0")));
        assertThat(read(inv).podCount()).isEqualTo(1);
        assertThat(read(inv).zoneCount()).isNull();
        inv.put("warnings", List.of(Map.of("resource", "pod-zones", "code", "COLLECTION_FAILED")));
        assertThat(read(inv).zoneCount()).isNull();
    }

    @Test void unrelatedWarningsDoNotHideObservedCounts() {
        var inv = inventory();
        inv.put("warnings", List.of(Map.of("resource", "hpa", "code", "PERMISSION_DENIED")));
        assertThat(read(inv).podCount()).isZero();
        assertThat(read(inv).zoneCount()).isZero();
    }

    @Test void clusterZonesAreNotInstallationZones() {
        var inv = inventory();
        inv.remove("topology");
        inv.put("cluster", Map.of("zoneCount", 3));
        assertThat(read(inv).zoneCount()).isNull();
    }
}
