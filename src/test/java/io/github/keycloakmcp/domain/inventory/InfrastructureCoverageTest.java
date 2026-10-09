package io.github.keycloakmcp.domain.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.keycloakmcp.domain.inventory.InfrastructureInventoryFixtures.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.keycloakmcp.discovery.ClusterApiCapabilities;
import io.github.keycloakmcp.discovery.ClusterApiCapabilities.ApiAvailability;
import io.github.keycloakmcp.discovery.DetectionConfidence;
import io.github.keycloakmcp.discovery.EnvironmentInfo;
import io.github.keycloakmcp.discovery.RuntimeType;
import io.github.keycloakmcp.target.InfrastructureType;

class InfrastructureCoverageTest {
    @Test
    void completeCollectionDoesNotRequireFavorableConfigurationOrExposure() {
        var inventory = complete("target-a");
        assertThat(InfrastructureCoverage.isComplete(inventory)).isTrue();
        assertThat(inventory.networking().routeOrIngressPresent()).isFalse();
        assertThat(inventory.hpa().present()).isFalse();
        assertThat(inventory.probes().readinessPresent()).isFalse();
    }

    @Test
    void observedScaleToZeroAndEmptyTopologyRemainComplete() {
        assertThat(InfrastructureCoverage.isComplete(scaledToZero("target-a"))).isTrue();
    }

    @ParameterizedTest
    @MethodSource("unobservedInventories")
    void missingCoverageIsIncompleteEvenWithoutWarnings(InfrastructureInventory inventory) {
        assertThat(InfrastructureCoverage.isComplete(inventory)).isFalse();
    }

    static Stream<InfrastructureInventory> unobservedInventories() {
        var observed = complete("target-a");
        return Stream.of(null, withoutRuntimeObservation("target-a"), withoutVersionObservation("target-a"),
                observed("target-a", observed.runtime(), observed.cluster(), 1, observed.pods(), observed.topology(), null),
                observed("target-a", observed.runtime(), observed.cluster(), 1, observed.pods(),
                        new TopologyInfo(Map.of("zone-a", 2), Map.of("node-a", 1), 1), List.of()),
                observed("target-a", observed.runtime(), observed.cluster(), 1,
                        List.of(new PodInventoryItem("sso-0", "node-a", null, true, 0, false)),
                        new TopologyInfo(Map.of(), Map.of("node-a", 1), 0), List.of()),
                observed("target-a", observed.runtime(), observed.cluster(), 1,
                        Arrays.asList((PodInventoryItem) null), observed.topology(), List.of()),
                observed("target-a", observed.runtime(), observed.cluster(), 1,
                        List.of(new PodInventoryItem("sso-0", "node-a", "zone-a", true, -1, false)),
                        observed.topology(), List.of()));
    }

    @Test
    void warningsPreventCompleteCoverageWithoutDiscardingObservedValues() {
        var observed = complete("target-a");
        var inventory = observed("target-a", observed.runtime(), observed.cluster(), 1,
                observed.pods(), observed.topology(), List.of(CollectionWarning.permissionDenied("networking", null)));
        assertThat(InfrastructureCoverage.isComplete(inventory)).isFalse();
        assertThat(inventory.pods()).isEqualTo(observed.pods());
    }

    @ParameterizedTest
    @MethodSource("untrustedDiscovery")
    void incompleteOrForeignDiscoveryCannotEstablishCoverage(EnvironmentInfo discovery) {
        assertThat(InfrastructureCoverage.isComplete(withDiscovery(complete("target-a"), discovery))).isFalse();
    }

    static Stream<EnvironmentInfo> untrustedDiscovery() {
        var absent = new ClusterApiCapabilities(ApiAvailability.NOT_SERVED, ApiAvailability.NOT_SERVED);
        return Stream.of(null,
                new EnvironmentInfo(RuntimeType.KUBERNETES, DetectionConfidence.CONFIRMED, "kubernetes", "ns",
                        List.of(), "target-a", "v1.32.2", "kubernetes"),
                new EnvironmentInfo(RuntimeType.KUBERNETES, DetectionConfidence.CONFIRMED, "kubernetes", "ns",
                        List.of(), "target-a", null, "kubernetes", InfrastructureType.KUBERNETES, absent),
                discovery("target-b", "ns", RuntimeType.KUBERNETES, DetectionConfidence.CONFIRMED, absent),
                discovery("target-a", "other-namespace", RuntimeType.KUBERNETES, DetectionConfidence.CONFIRMED, absent),
                discovery("target-a", "ns", RuntimeType.OPENSHIFT, DetectionConfidence.CONFIRMED,
                        new ClusterApiCapabilities(ApiAvailability.SERVED, ApiAvailability.SERVED)),
                discovery("target-a", "ns", RuntimeType.KUBERNETES, DetectionConfidence.DETECTED, absent),
                discovery("target-a", "ns", RuntimeType.KUBERNETES, DetectionConfidence.CONFIRMED,
                        new ClusterApiCapabilities(ApiAvailability.UNKNOWN, ApiAvailability.NOT_SERVED)),
                discovery("target-a", "ns", RuntimeType.KUBERNETES, DetectionConfidence.CONFIRMED,
                        new ClusterApiCapabilities(ApiAvailability.NOT_SERVED, ApiAvailability.UNKNOWN)),
                discovery("target-a", "ns", RuntimeType.KUBERNETES, DetectionConfidence.CONFIRMED,
                        new ClusterApiCapabilities(ApiAvailability.UNSUPPORTED_VERSION, ApiAvailability.NOT_SERVED)),
                discovery("target-a", "ns", RuntimeType.KUBERNETES, DetectionConfidence.CONFIRMED,
                        new ClusterApiCapabilities(ApiAvailability.NOT_SERVED, ApiAvailability.UNSUPPORTED_VERSION)),
                discovery("target-a", "ns", RuntimeType.KUBERNETES, DetectionConfidence.CONFIRMED,
                        new ClusterApiCapabilities(ApiAvailability.SERVED, ApiAvailability.NOT_SERVED)));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(ApiAvailability.class)
    void openshiftRequiresObservedSupportedConfigurationApi(ApiAvailability config) {
        var base = complete("target-a");
        var openshift = observed("target-a", "OPENSHIFT", new ClusterInfo("openshift", "4.18.0", "BareMetal", 1, 1),
                1, base.pods(), base.topology(), List.of());
        openshift = withDiscovery(openshift, discovery("target-a", "ns", RuntimeType.OPENSHIFT,
                DetectionConfidence.CONFIRMED, new ClusterApiCapabilities(ApiAvailability.SERVED, config)));
        assertThat(InfrastructureCoverage.isComplete(openshift)).isEqualTo(config == ApiAvailability.SERVED);
    }

    private static EnvironmentInfo discovery(String target, String namespace, RuntimeType runtime,
            DetectionConfidence confidence, ClusterApiCapabilities capabilities) {
        return new EnvironmentInfo(runtime, confidence, runtime.name().toLowerCase(java.util.Locale.ROOT), namespace,
                List.of(), target, "v1.32.2", "kubernetes", InfrastructureType.KUBERNETES, capabilities);
    }
}
