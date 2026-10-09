package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReportInventoryProjectionTest {
    private Map<String, Object> inventory() {
        return new LinkedHashMap<>(Map.of(
                "cluster", Map.of("version", "v1", "nodeCount", 3, "zoneCount", 2),
                "keycloak", Map.of("name", "sso", "readyReplicas", 0, "desiredReplicas", 0),
                "pods", List.of(), "topology", Map.of("zoneCount", 0, "podsByZone", Map.of()),
                "hpa", Map.of("present", false, "minReplicas", -1),
                "pdb", Map.of("present", false), "scheduling", Map.of("zoneSpreadPresent", false),
                "networking", Map.of("complete", true, "tlsEnabled", false, "services", List.of("owned-service")),
                "warnings", List.of()));
    }

    private Map<?, ?> project(Map<String, Object> inventory) {
        return (Map<?, ?>) ReportInventoryProjection.project(Map.of("inventory", inventory)).get("inventory");
    }

    @Test
    void commonBudgetWarningDoesNotErasePreviouslyObservedSections() {
        var raw = inventory();
        raw.put("warnings", List.of(Map.of("code", "OPERATION_BUDGET_EXCEEDED", "resource", "collection-budget")));
        var result = project(raw);
        assertThat(result.get("keycloak")).isEqualTo(raw.get("keycloak"));
        assertThat(result.get("cluster")).isEqualTo(raw.get("cluster"));
        assertThat(result.get("networking")).isEqualTo(raw.get("networking"));
    }

    @Test
    void noInfrastructureDoesNotExposeDefaultsAndDoesNotMutateSnapshot() {
        var raw = inventory();
        var result = (Map<?, ?>) ReportInventoryProjection.project(Map.of("infraType", "NONE", "inventory", raw)).get("inventory");
        assertThat(result.get("cluster")).isNull();
        for (String section : List.of("keycloak", "pods", "topology", "hpa", "pdb", "scheduling", "networking")) {
            assertThat(result.get(section)).isNull();
            assertThat(raw.get(section)).isNotNull();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOT_CONFIGURED", "BINDING_REQUIRED", "PERMISSION_DENIED"})
    void missingInfrastructureOrInstallationSuppressesGuessedAbsence(String code) {
        var raw = inventory();
        raw.put("warnings", List.of(Map.of("code", code, "resource", code.equals("NOT_CONFIGURED") ? "infrastructure" : "installation")));
        assertThat(project(raw).get("pods")).isNull();
        assertThat(project(raw).get("networking")).isNull();
    }

    @Test
    void observedZerosAndFalseRemainWhileNegativeSentinelsBecomeNull() {
        var result = project(inventory());
        assertThat(((Map<?, ?>) result.get("keycloak")).get("readyReplicas")).isEqualTo(0);
        assertThat(((Map<?, ?>) result.get("hpa")).get("present")).isEqualTo(false);
        assertThat(((Map<?, ?>) result.get("hpa")).get("minReplicas")).isNull();
        assertThat(((Map<?, ?>) result.get("topology")).get("zoneCount")).isEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hpa", "pdb", "pods"})
    void resourceDenialDoesNotEraseIndependentEvidence(String resource) {
        var raw = inventory();
        raw.put("warnings", List.of(Map.of("code", "PERMISSION_DENIED", "resource", resource)));
        var result = project(raw);
        assertThat(result.get(resource)).isNull();
        assertThat(result.get("keycloak")).isEqualTo(raw.get("keycloak"));
        assertThat(result.get("networking")).isEqualTo(raw.get("networking"));
        if (resource.equals("pods")) assertThat(result.get("topology")).isNull();
    }

    @Test
    void missingZonesDoNotBecomeZeroAndClusterVersionSurvivesNodeDenial() {
        var raw = inventory();
        raw.put("warnings", List.of(Map.of("resource", "nodes", "code", "PERMISSION_DENIED")));
        var result = project(raw);
        assertThat(((Map<?, ?>) result.get("topology")).get("zoneCount")).isNull();
        assertThat(((Map<?, ?>) result.get("cluster")).get("nodeCount")).isNull();
        assertThat(((Map<?, ?>) result.get("cluster")).get("version")).isEqualTo("v1");
    }

    @Test
    void incompleteNetworkingKeepsAssociationsWithoutFalseNegativeTls() {
        var raw = inventory();
        raw.put("warnings", List.of(Map.of("resource", "networking", "code", "COLLECTION_FAILED")));
        var network = (Map<?, ?>) project(raw).get("networking");
        assertThat(network.get("tlsEnabled")).isNull();
        assertThat(network.get("complete")).isEqualTo(false);
        assertThat(network.get("services")).isEqualTo(List.of("owned-service"));
    }

    @Test
    void missingTemplateDoesNotEraseKnownWorkload() {
        var raw = inventory();
        raw.put("warnings", List.of(Map.of("resource", "workload", "code", "COLLECTION_FAILED")));
        var result = project(raw);
        assertThat(result.get("keycloak")).isEqualTo(raw.get("keycloak"));
        assertThat(result.get("scheduling")).isNull();
    }

    @Test
    void opaqueFailureAndLegacyWarningsCannotExposeDefaultValues() {
        var raw = inventory();
        raw.put("warnings", List.of("legacy denied query"));
        assertThat(project(raw).get("cluster")).isNull();
        assertThat(project(raw).get("hpa")).isNull();
    }

    @Test
    void invalidCountsAreNotAcceptedAsObservations() {
        for (Object invalid : List.of(-1, 0.5, Double.NaN, Double.POSITIVE_INFINITY, "unknown", "-1", Long.MAX_VALUE)) {
            var raw = inventory();
            raw.put("keycloak", Map.of("name", "sso", "readyReplicas", invalid));
            assertThat(((Map<?, ?>) project(raw).get("keycloak")).get("readyReplicas")).isNull();
        }
    }

    @Test
    void unlabelledPodsDoNotImplyZeroObservedZones() {
        var raw = inventory();
        raw.put("pods", List.of(Map.of("name", "sso-0")));
        assertThat(((Map<?, ?>) project(raw).get("topology")).get("zoneCount")).isNull();
        assertThat(project(raw).get("pods")).isEqualTo(raw.get("pods"));
    }
}
