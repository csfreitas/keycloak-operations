package io.github.keycloakmcp.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.adapter.infrastructure.InfrastructureClientFactory;
import io.github.keycloakmcp.config.DiscoveryConfig;
import io.github.keycloakmcp.discovery.ClusterApiCapabilities.ApiAvailability;
import io.github.keycloakmcp.target.*;

@ExtendWith(MockitoExtension.class)
@EnableKubernetesMockClient
class EnvironmentDiscoveryTest {
    private static final String CANARY = "never-export-discovery-credential-canary";
    private static final String NAMESPACE = "registered-namespace";

    KubernetesMockServer server;
    KubernetesClient client;

    @Mock
    private DiscoveryConfig discoveryConfig;

    @Mock
    private ClusterClient cluster;

    @Mock
    private InfrastructureClientFactory clientFactory;

    private EnvironmentDiscovery discovery;
    private int discoveryCalls = 1;

    @BeforeEach
    void setUp() {
        // The mock server registers an always-matching /version response without
        // gitVersion. Remove it so every test exercises its own exact HTTP fixture.
        server.clearExpectations();
        client.getConfiguration().setRequestRetryBackoffLimit(0);
        discovery = new EnvironmentDiscovery(discoveryConfig, clientFactory);
    }

    @AfterEach
    void onlyFixedReadPathsAndNoImplicitPlatformDetection() throws InterruptedException {
        verify(cluster, never()).type();
        verify(cluster, never()).openshift();
        verifyNoInteractions(discoveryConfig);
        int apiGroups = 0;
        int versions = 0;
        for (int i = 0, count = server.getRequestCount(); i < count; i++) {
            var request = server.takeRequest(1, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getPath()).isIn("/apis", "/version");
            if ("/apis".equals(request.getPath())) apiGroups++;
            else versions++;
        }
        assertThat(apiGroups).isLessThanOrEqualTo(discoveryCalls);
        assertThat(versions).isLessThanOrEqualTo(discoveryCalls);
    }

    @Test
    void legacyGlobalDiscoveryNeverProbesAmbientInfrastructure() {
        EnvironmentInfo info = discovery.discover();

        assertThat(info.runtime()).isEqualTo(RuntimeType.UNKNOWN);
        assertThat(info.confidence()).isEqualTo(DetectionConfidence.UNKNOWN);
        assertThat(info.platform()).isEqualTo("unknown");
        assertThat(info.namespace()).isNull();
        assertThat(info.evidence())
                .anyMatch(e -> e.contains("Global discovery is disabled"));
        verifyNoInteractions(discoveryConfig, clientFactory);
    }

    @Test
    void missingTargetNeverUsesAmbientDiscovery() {
        assertThat(discovery.discover(null).runtime()).isEqualTo(RuntimeType.UNKNOWN);
        verifyNoInteractions(discoveryConfig, clientFactory);
    }

    @Test
    void absentInfrastructurePreservesTargetWithoutProbingCluster() {
        assertScopedUnknown(target(null));
    }

    @Test
    void explicitlyDisabledInfrastructureNeverUsesGlobalDiscovery() {
        assertScopedUnknown(target(new InfrastructureTargetConfiguration(InfrastructureType.NONE, null, null, null)));
    }

    @Test
    void declaredVmDoesNotBecomeAnObservedClusterOrConfirmedVm() {
        assertScopedUnknown(target(new InfrastructureTargetConfiguration(InfrastructureType.VM, null, null, "host-ref")));
    }

    @Test
    void clusterWithoutCredentialBindingNeverUsesAmbientCredentials() {
        for (String ref : new String[] {null, "", " "}) {
            assertScopedUnknown(target(new InfrastructureTargetConfiguration(InfrastructureType.OPENSHIFT, "cluster", "ns", ref)));
        }
    }

    @Test
    void explicitlyBoundClusterResolvesOnlyTheTargetClient() {
        Target target = target(new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "cluster", "ns", "infra-ref"));
        when(clientFactory.resolve(target)).thenReturn(java.util.Optional.empty());
        EnvironmentInfo info = discovery.discover(target);
        assertThat(info.targetId()).isEqualTo("portable-target");
        assertThat(info.runtime()).isEqualTo(RuntimeType.UNKNOWN);
        verify(clientFactory).resolve(target);
        verifyNoInteractions(discoveryConfig);
    }

    @Test
    void missingConnectionOrNamespaceCannotUseAmbientScope() {
        for (String value : new String[] {null, "", " "}) {
            assertScopedUnknown(target(new InfrastructureTargetConfiguration(
                    InfrastructureType.KUBERNETES, value, NAMESPACE, "infra-ref")));
            assertScopedUnknown(target(new InfrastructureTargetConfiguration(
                    InfrastructureType.KUBERNETES, "cluster", value, "infra-ref")));
        }
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void wrapperNamespaceMismatchFailsBeforeObtainingTransport() {
        Target target = configured(InfrastructureType.KUBERNETES);
        when(clientFactory.resolve(target)).thenReturn(Optional.of(cluster));
        when(cluster.namespace()).thenReturn("another-target");
        assertUnknown(discovery.discover(target));
        verify(cluster, never()).kubernetes();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void interruptedCallDoesNotResolveClientOrIssueHttp() {
        try {
            Thread.currentThread().interrupt();
            assertUnknown(discovery.discover(configured(InfrastructureType.OPENSHIFT)));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verifyNoInteractions(clientFactory, cluster);
            assertThat(server.getRequestCount()).isZero();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void clientResolutionFailureReturnsSafeUnknownWithoutHttp() {
        Target target = configured(InfrastructureType.KUBERNETES);
        when(clientFactory.resolve(target)).thenThrow(new IllegalStateException(CANARY,
                new IllegalArgumentException("Bearer " + CANARY)));
        assertSafeDiagnostics(() -> assertUnknown(discovery.discover(target)));
        assertThat(server.getRequestCount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = InfrastructureType.class, names = {"KUBERNETES", "OPENSHIFT"})
    void observedCompleteApiGroupListDeterminesKubernetesIndependentlyOfConfiguredType(InfrastructureType configured) {
        Target target = bind(configured);
        respondApiGroups(apiGroups(List.of(group("apps"))));
        respondVersion(200, Map.of("gitVersion", "v1.32.2"));
        EnvironmentInfo result = discovery.discover(target);
        assertConfirmed(result, RuntimeType.KUBERNETES, "kubernetes");
        assertThat(result.clusterVersion()).isEqualTo("v1.32.2");
        assertThat(result.configuredType()).isEqualTo(configured);
        assertThat(result.apiCapabilities()).isEqualTo(new ClusterApiCapabilities(
                ApiAvailability.NOT_SERVED, ApiAvailability.NOT_SERVED));
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"route.openshift.io", "config.openshift.io"})
    void oneObservedOpenShiftGroupIsSufficientWithoutRepeatedProbes(String name) {
        Target target = bind(InfrastructureType.KUBERNETES);
        respondApiGroups(apiGroups(List.of(group(name))));
        respondVersion(200, Map.of("gitVersion", "v1.32.2+openshift"));
        EnvironmentInfo result = discovery.discover(target);
        assertConfirmed(result, RuntimeType.OPENSHIFT, "openshift");
        assertThat(result.clusterVersion()).isEqualTo("v1.32.2+openshift");
        assertThat(result.evidence()).contains("API group present: " + name);
        assertThat(result.configuredType()).isEqualTo(InfrastructureType.KUBERNETES);
        assertThat(result.apiCapabilities().routeV1()).isEqualTo(name.equals("route.openshift.io")
                ? ApiAvailability.SERVED : ApiAvailability.NOT_SERVED);
        assertThat(result.apiCapabilities().configV1()).isEqualTo(name.equals("config.openshift.io")
                ? ApiAvailability.SERVED : ApiAvailability.NOT_SERVED);
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void completeEmptyGroupListCanConfirmKubernetesNotConfiguredOpenShift() {
        Target target = bind(InfrastructureType.OPENSHIFT);
        respondApiGroups(apiGroups(List.of()));
        respondVersion(200, Map.of("gitVersion", "v1.32.2"));
        EnvironmentInfo result = discovery.discover(target);
        assertConfirmed(result, RuntimeType.KUBERNETES, "kubernetes");
        assertThat(result.clusterVersion()).isEqualTo("v1.32.2");
        assertThat(result.configuredType()).isEqualTo(InfrastructureType.OPENSHIFT);
        assertThat(result.apiCapabilities()).isEqualTo(new ClusterApiCapabilities(
                ApiAvailability.NOT_SERVED, ApiAvailability.NOT_SERVED));
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"route.openshift.io", "config.openshift.io"})
    void observedOpenShiftGroupWithOnlyV2DoesNotAdvertiseV1Capability(String name) {
        Target target = bind(InfrastructureType.OPENSHIFT);
        respondApiGroups(apiGroups(List.of(group(name, "v2"))));
        respondVersion(200, Map.of("gitVersion", "v1.32.2"));

        EnvironmentInfo result = discovery.discover(target);

        assertConfirmed(result, RuntimeType.OPENSHIFT, "openshift");
        assertThat(result.apiCapabilities().routeV1()).isEqualTo(name.equals("route.openshift.io")
                ? ApiAvailability.UNSUPPORTED_VERSION : ApiAvailability.NOT_SERVED);
        assertThat(result.apiCapabilities().configV1()).isEqualTo(name.equals("config.openshift.io")
                ? ApiAvailability.UNSUPPORTED_VERSION : ApiAvailability.NOT_SERVED);
    }

    @Test
    void allServedVersionsAreConsideredWithoutAssumingPreferredVersion() {
        Target target = bind(InfrastructureType.KUBERNETES);
        var route = new LinkedHashMap<>(group("route.openshift.io", "v1", "v2"));
        route.put("preferredVersion", Map.of("version", "v2", "groupVersion", "route.openshift.io/v2"));
        respondApiGroups(apiGroups(List.of(route, group("config.openshift.io", "v2", "v1"))));
        respondVersion(200, Map.of("gitVersion", "v1.32.2"));

        EnvironmentInfo result = discovery.discover(target);

        assertConfirmed(result, RuntimeType.OPENSHIFT, "openshift");
        assertThat(result.apiCapabilities()).isEqualTo(new ClusterApiCapabilities(
                ApiAvailability.SERVED, ApiAvailability.SERVED));
    }

    @Test
    void historicalEnvironmentConstructorsCannotInventObservedCapabilities() {
        var oldFull = new EnvironmentInfo(RuntimeType.OPENSHIFT, DetectionConfidence.CONFIRMED,
                "openshift", NAMESPACE, List.of(), "portable-target", "v1.32.2", "openshift");
        var oldCompact = new EnvironmentInfo(RuntimeType.OPENSHIFT, DetectionConfidence.CONFIRMED,
                "openshift", NAMESPACE, List.of());

        assertThat(oldFull.apiCapabilities()).isEqualTo(ClusterApiCapabilities.unknown());
        assertThat(oldCompact.apiCapabilities()).isEqualTo(ClusterApiCapabilities.unknown());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 429, 500, 503})
    void apiGroupFailureCannotConfirmAnyRuntimeOrRetry(int status) {
        Target target = bind(InfrastructureType.OPENSHIFT);
        server.expect().get().withPath("/apis").andReturn(status, Map.of("message", CANARY)).always();
        assertSafeDiagnostics(() -> assertUnknown(discovery.discover(target)));
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("invalidApiGroups")
    void malformedOrIncompleteApiGroupsNeverBecomeObservedKubernetes(Object body) {
        Target target = bind(InfrastructureType.KUBERNETES);
        respondApiGroups(body);
        assertSafeDiagnostics(() -> assertUnknown(discovery.discover(target)));
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    static Stream<Object> invalidApiGroups() {
        var noGroups = Map.of("apiVersion", "v1", "kind", "APIGroupList");
        var invalidPreferred = new LinkedHashMap<>(group("route.openshift.io"));
        invalidPreferred.put("preferredVersion", Map.of("version", "v2", "groupVersion", "route.openshift.io/v2"));
        return Stream.of(
                Map.of(),
                Map.of("apiVersion", "v1", "kind", "Status", "groups", List.of()),
                Map.of("apiVersion", "v2", "kind", "APIGroupList", "groups", List.of()),
                noGroups,
                "{\"apiVersion\":\"v1\",\"kind\":\"APIGroupList\",\"groups\":null}",
                Map.of("apiVersion", "v1", "kind", "APIGroupList", "groups", Map.of()),
                apiGroups(List.of(Map.of())),
                apiGroups(List.of(group("INVALID." + CANARY))),
                apiGroups(List.of(Map.of("name", "route.openshift.io"))),
                apiGroups(List.of(Map.of("name", "route.openshift.io", "versions", List.of()))),
                apiGroups(List.of(Map.of("name", "route.openshift.io", "versions",
                        List.of(Map.of("version", "v1", "groupVersion", "other/v1"))))),
                apiGroups(List.of(group("route.openshift.io"), group("route.openshift.io"))),
                apiGroups(List.of(invalidPreferred)),
                apiGroups(IntStream.range(0, 101).mapToObj(i -> group("api" + i + ".example.test")).toList()),
                "{\"apiVersion\":\"v1\",\"kind\":\"APIGroupList\",\"groups\":[{\"name\":\"" + CANARY,
                "{\"apiVersion\":\"v1\",\"kind\":\"APIGroupList\",\"groups\":[],\"groups\":[]}");
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 429, 500, 503})
    void versionFailureRetainsIndependentlyObservedOpenShift(int status) {
        Target target = bind(InfrastructureType.KUBERNETES);
        respondApiGroups(apiGroups(List.of(group("config.openshift.io"))));
        respondVersion(status, Map.of("message", CANARY));
        assertSafeDiagnostics(() -> {
            EnvironmentInfo result = discovery.discover(target);
            assertConfirmed(result, RuntimeType.OPENSHIFT, "openshift");
            assertThat(result.clusterVersion()).isNull();
            assertThat(result.configuredType()).isEqualTo(InfrastructureType.KUBERNETES);
            assertThat(result.apiCapabilities()).isEqualTo(new ClusterApiCapabilities(
                    ApiAvailability.NOT_SERVED, ApiAvailability.SERVED));
        });
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("invalidVersions")
    void malformedVersionCannotInvalidateObservedKubernetesOrLeakPayload(Object body) {
        Target target = bind(InfrastructureType.OPENSHIFT);
        respondApiGroups(apiGroups(List.of(group("apps"))));
        respondVersion(200, body);
        assertSafeDiagnostics(() -> {
            EnvironmentInfo result = discovery.discover(target);
            assertConfirmed(result, RuntimeType.KUBERNETES, "kubernetes");
            assertThat(result.clusterVersion()).isNull();
        });
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    static Stream<Object> invalidVersions() {
        return Stream.of(Map.of(), Map.of("gitVersion", 132), Map.of("gitVersion", ""),
                Map.of("gitVersion", "v1.32.2 Bearer " + CANARY),
                Map.of("gitVersion", "v".repeat(129)),
                "{\"gitVersion\":\"" + CANARY,
                "{\"gitVersion\":null}");
    }

    @Test
    void failedObservationIsNotCachedAsAbsenceAndNextCallCanObserveOpenShift() {
        discoveryCalls = 2;
        Target target = bind(InfrastructureType.OPENSHIFT);
        server.expect().get().withPath("/apis").andReturn(503, Map.of("message", CANARY)).once();
        respondApiGroups(apiGroups(List.of(group("route.openshift.io"), group("config.openshift.io"))));
        respondVersion(200, Map.of("gitVersion", "v1.32.2"));
        assertUnknown(discovery.discover(target));
        assertConfirmed(discovery.discover(target), RuntimeType.OPENSHIFT, "openshift");
        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    private Target bind(InfrastructureType type) {
        Target target = configured(type);
        when(clientFactory.resolve(target)).thenReturn(Optional.of(cluster));
        when(cluster.namespace()).thenReturn(NAMESPACE);
        when(cluster.kubernetes()).thenReturn(client);
        return target;
    }

    private static Target configured(InfrastructureType type) {
        return target(new InfrastructureTargetConfiguration(type, "approved-cluster", NAMESPACE, "infra-ref"));
    }

    private static Map<String, Object> apiGroups(List<?> groups) {
        return Map.of("apiVersion", "v1", "kind", "APIGroupList", "groups", groups);
    }

    private static Map<String, Object> group(String name) {
        return group(name, "v1");
    }

    private static Map<String, Object> group(String name, String... versions) {
        return Map.of("name", name, "versions", Stream.of(versions)
                .map(version -> Map.of("version", version, "groupVersion", name + "/" + version)).toList());
    }

    private void respondApiGroups(Object body) {
        server.expect().get().withPath("/apis").andReturn(200, body).always();
    }

    private void respondVersion(int status, Object body) {
        server.expect().get().withPath("/version").andReturn(status, body).always();
    }

    private static void assertUnknown(EnvironmentInfo result) {
        assertThat(result.targetId()).isEqualTo("portable-target");
        assertThat(result.runtime()).isEqualTo(RuntimeType.UNKNOWN);
        assertThat(result.confidence()).isEqualTo(DetectionConfidence.UNKNOWN);
        assertThat(result.namespace()).isNull();
        assertThat(result.clusterVersion()).isNull();
        assertThat(result.clusterPlatform()).isNull();
        assertThat(result.apiCapabilities()).isEqualTo(ClusterApiCapabilities.unknown());
        assertThat(result.toString()).doesNotContain(CANARY);
    }

    private static void assertConfirmed(EnvironmentInfo result, RuntimeType runtime, String platform) {
        assertThat(result.targetId()).isEqualTo("portable-target");
        assertThat(result.runtime()).isEqualTo(runtime);
        assertThat(result.confidence()).isEqualTo(DetectionConfidence.CONFIRMED);
        assertThat(result.namespace()).isEqualTo(NAMESPACE);
        assertThat(result.platform()).isEqualTo(platform);
        assertThat(result.clusterPlatform()).isEqualTo(platform);
        assertThat(result.toString()).doesNotContain(CANARY);
    }

    private static void assertSafeDiagnostics(Runnable action) {
        var logger = java.util.logging.Logger.getLogger(EnvironmentDiscovery.class.getName());
        Level previous = logger.getLevel();
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        handler.setLevel(Level.ALL);
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
        try {
            action.run();
            assertThat(records).allSatisfy(record -> {
                assertThat(record.getMessage()).doesNotContain(CANARY);
                assertThat(record.getThrown()).isNull();
                if (record.getParameters() != null) {
                    assertThat(java.util.Arrays.toString(record.getParameters())).doesNotContain(CANARY);
                }
            });
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previous);
        }
    }

    private void assertScopedUnknown(Target target) {
        EnvironmentInfo info = discovery.discover(target);
        assertThat(info.targetId()).isEqualTo("portable-target");
        assertThat(info.runtime()).isEqualTo(RuntimeType.UNKNOWN);
        assertThat(info.confidence()).isEqualTo(DetectionConfidence.UNKNOWN);
        assertThat(info.clusterVersion()).isNull();
        assertThat(info.clusterPlatform()).isNull();
        assertThat(info.configuredType()).isEqualTo(target.infrastructureTypeOrNone());
        assertThat(info.apiCapabilities()).isEqualTo(ClusterApiCapabilities.unknown());
        assertThat(info.evidence()).isNotEmpty();
        verifyNoInteractions(discoveryConfig, clientFactory);
    }

    private static Target target(InfrastructureTargetConfiguration infrastructure) {
        return new Target(TargetId.of("portable-target"), "Portable target", TargetType.KEYCLOAK,
                TargetEnvironment.DEV, true,
                new KeycloakTargetConfiguration("http://localhost:18080", "fixture", "reader", "kc-ref"),
                infrastructure, null, java.util.Map.of());
    }
}
