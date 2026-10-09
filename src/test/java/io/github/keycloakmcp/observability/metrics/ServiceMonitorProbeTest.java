package io.github.keycloakmcp.observability.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.adapter.infrastructure.InfrastructureClientFactory;
import io.github.keycloakmcp.service.platform.InventoryService;
import io.github.keycloakmcp.target.InfrastructureTargetConfiguration;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.KubernetesInstallationBinding;
import io.github.keycloakmcp.target.ObservabilityTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetType;

@EnableKubernetesMockClient
class ServiceMonitorProbeTest {
    private static final String NAMESPACE = "iam";
    private static final String LIST_PATH = "/apis/monitoring.coreos.com/v1/namespaces/iam/servicemonitors?limit=101";
    private static final String CANARY = "never-export-provider-secret-canary";

    KubernetesMockServer server;
    KubernetesClient client;
    private InfrastructureClientFactory infrastructure;
    private MetricsProviderFactory metrics;
    private MetricsProvider provider;
    private InventoryService inventory;
    private ClusterClient cluster;
    private Target target;
    private Service service;
    private ServiceMonitorProbe probe;

    @BeforeEach
    void setUp() {
        infrastructure = mock(InfrastructureClientFactory.class);
        metrics = mock(MetricsProviderFactory.class);
        provider = mock(MetricsProvider.class);
        inventory = mock(InventoryService.class);
        cluster = mock(ClusterClient.class);
        target = target(true);
        service = service("svc-a", Map.of("app", "sso", "instance", "a", "tier", "auth"));
        when(infrastructure.resolve(target)).thenReturn(Optional.of(cluster));
        when(cluster.kubernetes()).thenReturn(client);
        when(cluster.namespace()).thenReturn(NAMESPACE);
        when(cluster.type()).thenReturn(InfrastructureType.KUBERNETES);
        when(inventory.associatedServices(target, cluster)).thenReturn(
                new InventoryService.ServiceAssociation(NAMESPACE, List.of(service), true));
        when(metrics.forTarget(target)).thenReturn(provider);
        when(provider.supported(target)).thenReturn(true);
        when(provider.probeScrape(target)).thenReturn(observation(2, 2, 0));
        probe = new ServiceMonitorProbe(infrastructure, metrics, inventory);
    }

    @AfterEach
    void onlyBoundedNamespaceReadsAndNoCountBasedScrapeProbe() throws InterruptedException {
        verify(provider, never()).probeSeries(any(), anyString());
        verify(provider, never()).probeSeries(any(), anyString(), any());
        for (int i = 0, count = server.getRequestCount(); i < count; i++) {
            var request = server.takeRequest(1, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getPath()).isEqualTo(LIST_PATH);
            assertThat(request.getPath()).doesNotContain("secrets", "/namespaces/other/");
        }
    }

    @Test
    void unboundTargetDoesNotCreateClusterOrMetricsWork() {
        assertUnknown(probe.probe(target(false)));
        verifyNoInteractions(infrastructure, inventory, metrics, provider);
    }

    @Test
    void expiredParentDoesNotStartClusterOrMetricsWork() {
        var clock = new AtomicLong();
        var budget = new CollectionBudget(Duration.ofMillis(100), clock::get);
        clock.set(Duration.ofMillis(100).toNanos());
        try (var scope = CollectionBudget.open(target.id().value(), budget)) {
            assertUnknown(probe.probe(target));
        }
        verifyNoInteractions(infrastructure, inventory, metrics, provider);
        assertThat(server.getRequestCount()).isZero();
        assertThat(CollectionBudget.current()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lateClientOrAssociationStopsBeforeMonitorHttp(boolean clientLate) {
        var clock = new AtomicLong();
        if (clientLate) {
            when(infrastructure.resolve(target)).thenAnswer(inv -> {
                clock.set(Duration.ofMillis(100).toNanos());
                return Optional.of(cluster);
            });
        } else {
            when(inventory.associatedServices(target, cluster)).thenAnswer(inv -> {
                clock.set(Duration.ofMillis(100).toNanos());
                return new InventoryService.ServiceAssociation(NAMESPACE, List.of(service), true);
            });
        }
        try (var scope = CollectionBudget.open(target.id().value(), new CollectionBudget(Duration.ofMillis(100), clock::get))) {
            assertUnknown(probe.probe(target));
        }
        if (clientLate) verifyNoInteractions(inventory);
        verifyNoInteractions(metrics, provider);
        assertThat(server.getRequestCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"factory", "supported", "scrape"})
    void completedConfigurationSurvivesMetricsAbortButLateHealthDoesNot(String phase) {
        respond(List.of(monitor("matching", normalSpec())));
        var clock = new AtomicLong();
        if (phase.equals("factory")) {
            when(metrics.forTarget(target)).thenAnswer(inv -> {
                clock.set(Duration.ofSeconds(10).toNanos());
                return provider;
            });
        } else if (phase.equals("supported")) {
            when(provider.supported(target)).thenAnswer(inv -> {
                clock.set(Duration.ofSeconds(10).toNanos());
                return true;
            });
        } else {
            when(provider.probeScrape(target)).thenAnswer(inv -> {
                clock.set(Duration.ofSeconds(10).toNanos());
                return observation(2, 2, 0);
            });
        }
        try (var scope = CollectionBudget.open(target.id().value(), new CollectionBudget(Duration.ofSeconds(10), clock::get))) {
            var result = probe.probe(target);
            assertThat(result.readiness()).isEqualTo(ScrapeReadiness.UNKNOWN);
            assertThat(result.serviceMonitorPresent()).isTrue();
            assertThat(result.detail()).isEqualTo("Matching scrape observations unavailable");
            assertThat(CollectionBudget.current()).isSameAs(scope.budget());
        }
        if (!phase.equals("scrape")) verify(provider, never()).probeScrape(target);
        assertThat(CollectionBudget.current()).isNull();
    }

    @Test
    void namespaceMismatchCannotUseAClientForAnotherNamespace() {
        when(cluster.namespace()).thenReturn("other");
        assertUnknown(probe.probe(target));
        verifyNoInteractions(inventory, metrics, provider);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingAssociatedServicesIsNotEvidenceOfMissingServiceMonitor(boolean complete) {
        when(inventory.associatedServices(target, cluster)).thenReturn(
                new InventoryService.ServiceAssociation(NAMESPACE, List.of(), complete));
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    @Test
    void partialServiceAssociationCannotClaimMonitorPresence() {
        when(inventory.associatedServices(target, cluster)).thenReturn(
                new InventoryService.ServiceAssociation(NAMESPACE, List.of(service), false));
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    @Test
    void associationNamespaceMismatchIsUnknown() {
        when(inventory.associatedServices(target, cluster)).thenReturn(
                new InventoryService.ServiceAssociation("other", List.of(service), true));
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    @Test
    void completeEmptyMonitorListProvesMissingMonitorForKnownServices() {
        respond(List.of());
        var result = probe.probe(target);
        assertThat(result.readiness()).isEqualTo(ScrapeReadiness.SERVICEMONITOR_MISSING);
        assertThat(result.serviceMonitorPresent()).isFalse();
        assertThat(result.interval()).isNull();
        assertThat(result.scrapeTimeout()).isNull();
        verifyNoInteractions(metrics, provider);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unrelatedFirstMonitorIsIgnoredRegardlessOfListOrder(boolean reversed) {
        var unrelated = monitor("unrelated", spec(Map.of("matchLabels", Map.of("instance", "b")),
                List.of(Map.of("port", "management", "interval", "2m", "scrapeTimeout", "15s"))));
        var matching = monitor("matching", normalSpec());
        respond(reversed ? List.of(matching, unrelated) : List.of(unrelated, matching));

        var result = probe.probe(target);
        assertThat(result.readiness()).isEqualTo(ScrapeReadiness.SCRAPE_HEALTHY);
        assertThat(result.serviceMonitorPresent()).isTrue();
        assertThat(result.interval()).isEqualTo("30s");
        assertThat(result.scrapeTimeout()).isEqualTo("10s");
        verify(provider).probeScrape(target);
    }

    @Test
    void labelSelectorMatchesServiceMetadataAndNotItsPodSelector() {
        respond(List.of(monitor("pod-selector-only", spec(
                Map.of("matchLabels", Map.of("pod-app", "keycloak-a")), List.of(endpoint())))));
        var result = probe.probe(target);
        assertThat(result.readiness()).isEqualTo(ScrapeReadiness.SERVICEMONITOR_MISSING);
        assertThat(result.serviceMonitorPresent()).isFalse();
        verifyNoInteractions(metrics, provider);
    }

    @ParameterizedTest
    @MethodSource("matchingExpressions")
    void supportedSetSelectorsAssociateTheService(Map<String, Object> expression) {
        respond(List.of(monitor("matching", spec(Map.of("matchExpressions", List.of(expression)), List.of(endpoint())))));
        assertThat(probe.probe(target).readiness()).isEqualTo(ScrapeReadiness.SCRAPE_HEALTHY);
    }

    static Stream<Map<String, Object>> matchingExpressions() {
        return Stream.of(
                Map.of("key", "instance", "operator", "In", "values", List.of("a")),
                Map.of("key", "instance", "operator", "NotIn", "values", List.of("b")),
                Map.of("key", "tier", "operator", "Exists"),
                Map.of("key", "absent", "operator", "DoesNotExist"));
    }

    @ParameterizedTest
    @MethodSource("invalidSelectors")
    void malformedSelectorIsUnknownRatherThanAbsentOrHealthy(Object selector) {
        respond(List.of(monitor("matching", spec(selector, List.of(endpoint())))));
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    static Stream<Object> invalidSelectors() {
        return Stream.of("selector-" + CANARY,
                Map.of("matchLabels", Map.of("instance", 1)),
                Map.of("matchExpressions", "not-a-list"),
                Map.of("matchExpressions", List.of(Map.of("key", "instance", "operator", "Unknown", "values", List.of("a")))),
                Map.of("matchExpressions", List.of(Map.of("key", "instance", "operator", "In", "values", List.of()))),
                Map.of("matchExpressions", List.of(Map.of("key", "instance", "operator", "Exists", "values", List.of("a")))));
    }

    @ParameterizedTest
    @MethodSource("invalidLabelGrammar")
    void malformedLabelGrammarCannotBecomeAFavorableSelectorMatch(String key, String value) {
        service.getMetadata().setLabels(Map.of(key, value));
        respond(List.of(monitor("matching", spec(Map.of("matchLabels", Map.of(key, value)), List.of(endpoint())))));
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    static Stream<Arguments> invalidLabelGrammar() {
        return Stream.of(Arguments.of("a/b/c", "valid"), Arguments.of("invalid/", "valid"),
                Arguments.of("bad-", "valid"), Arguments.of("name".repeat(16), "valid"),
                Arguments.of("UPPER.example/name", "valid"), Arguments.of("valid", "-bad"),
                Arguments.of("valid", "bad_"), Arguments.of("valid", "."));
    }

    @ParameterizedTest
    @MethodSource("unsupportedNamespaces")
    void potentiallyMatchingCrossNamespaceMonitorRemainsUnknown(Map<String, Object> namespaceSelector) {
        Map<String, Object> spec = normalSpec();
        spec.put("namespaceSelector", namespaceSelector);
        respond(List.of(monitor("matching", spec)));
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    static Stream<Map<String, Object>> unsupportedNamespaces() {
        return Stream.of(Map.of("any", true), Map.of("matchNames", List.of("iam", "other")),
                Map.of("any", "true"), Map.of("matchNames", "iam"));
    }

    @Test
    void explicitCurrentNamespaceIsSupported() {
        Map<String, Object> spec = normalSpec();
        spec.put("namespaceSelector", Map.of("matchNames", List.of("iam")));
        respond(List.of(monitor("matching", spec)));
        assertThat(probe.probe(target).readiness()).isEqualTo(ScrapeReadiness.SCRAPE_HEALTHY);
    }

    @Test
    void selectorThatAlsoSelectsForeignServiceCannotClaimExclusiveMonitor() {
        Service foreign = service("svc-b", Map.of("app", "sso", "instance", "b"));
        when(inventory.associatedServices(target, cluster)).thenReturn(new InventoryService.ServiceAssociation(
                NAMESPACE, List.of(service), List.of(service, foreign), true));
        respond(List.of(monitor("shared", spec(Map.of("matchLabels", Map.of("app", "sso")), List.of(endpoint())))));
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    @ParameterizedTest
    @MethodSource("unsupportedEndpoints")
    void relatedMonitorWithUnsupportedEndpointIsUnknown(Object endpoint) {
        respond(List.of(monitor("matching", spec(labels(), List.of(endpoint)))));
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    static Stream<Object> unsupportedEndpoints() {
        return Stream.of(Map.of("targetPort", 9000), Map.of("port", 9000), Map.of("port", "missing"),
                Map.of("port", ""), Map.of("interval", "30s"), "endpoint-" + CANARY);
    }

    @Test
    void matchingNamedPortMustBeTcp() {
        service.getSpec().getPorts().getFirst().setProtocol("UDP");
        respond(List.of(monitor("matching", normalSpec())));
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0, 65536})
    void matchingNamedServicePortMustHaveValidPortNumber(Integer port) {
        service.getSpec().getPorts().getFirst().setPort(port);
        respond(List.of(monitor("matching", normalSpec())));
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    @ParameterizedTest
    @MethodSource("unsupportedRelabelings")
    void relabelingsCannotRewriteTheInferredScrapeDestinationOrIdentity(Object relabelings) {
        Map<String, Object> endpoint = new LinkedHashMap<>(endpoint());
        endpoint.put("relabelings", relabelings);
        respond(List.of(monitor("matching", spec(labels(), List.of(endpoint)))));
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    static Stream<Object> unsupportedRelabelings() {
        return Stream.of(List.of(Map.of("targetLabel", "__address__", "replacement", CANARY)),
                List.of(Map.of("targetLabel", "target_id", "replacement", "lab-b")),
                "malformed-" + CANARY, Map.of());
    }

    @Test
    void explicitlyEmptyRelabelingsAreSupported() {
        Map<String, Object> endpoint = new LinkedHashMap<>(endpoint());
        endpoint.put("relabelings", List.of());
        respond(List.of(monitor("matching", spec(labels(), List.of(endpoint)))));
        assertThat(probe.probe(target).readiness()).isEqualTo(ScrapeReadiness.SCRAPE_HEALTHY);
    }

    @Test
    void moreThanOneMatchingMonitorIsAmbiguous() {
        respond(List.of(monitor("one", normalSpec()), monitor("two", normalSpec())));
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    @Test
    void moreThanOneMatchingEndpointIsAmbiguous() {
        respond(List.of(monitor("matching", spec(labels(), List.of(endpoint(), endpoint())))));
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    @Test
    void moreThanOneAssociatedServiceSelectedIsAmbiguous() {
        Service second = service("svc-a-two", service.getMetadata().getLabels());
        when(inventory.associatedServices(target, cluster)).thenReturn(
                new InventoryService.ServiceAssociation(NAMESPACE, List.of(service, second), true));
        respond(List.of(monitor("matching", normalSpec())));
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    @ParameterizedTest
    @MethodSource("incompleteListMetadata")
    void paginatedOrIncompleteListsCannotProvePresenceOrAbsence(Map<String, Object> metadata) {
        respond(List.of(monitor("matching", normalSpec())), metadata);
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    static Stream<Map<String, Object>> incompleteListMetadata() {
        return Stream.of(Map.of("continue", "next-page"), Map.of("remainingItemCount", 1));
    }

    @Test
    void overLimitListIsRejectedWithoutChoosingFirst() {
        respond(IntStream.rangeClosed(0, 100).mapToObj(i -> monitor("monitor-" + i, normalSpec())).toList());
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    @Test
    void duplicateMonitorIdentityIsNotCollapsedIntoOneObservation() {
        var monitor = monitor("duplicate", normalSpec());
        respond(List.of(monitor, monitor));
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    @Test
    void malformedListItemsCannotProveAbsence() {
        server.expect().get().withPath(LIST_PATH).andReturn(200,
                Map.of("apiVersion", "monitoring.coreos.com/v1", "kind", "ServiceMonitorList",
                        "metadata", Map.of(), "items", "malformed-" + CANARY)).always();
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    @ParameterizedTest
    @MethodSource("invalidListEnvelopes")
    void malformedOrOmittedListEnvelopeFieldsCannotProveAbsence(Map<String, Object> envelope) {
        server.expect().get().withPath(LIST_PATH).andReturn(200, envelope).always();
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    static Stream<Map<String, Object>> invalidListEnvelopes() {
        return Stream.of(Map.of(),
                Map.of("apiVersion", "monitoring.coreos.com/v1", "kind", "ServiceMonitorList", "items", List.of()),
                Map.of("apiVersion", "monitoring.coreos.com/v99", "kind", "ServiceMonitorList",
                        "metadata", Map.of(), "items", List.of()),
                Map.of("apiVersion", "monitoring.coreos.com/v1", "kind", "WrongKind",
                        "metadata", Map.of(), "items", List.of()),
                Map.of("apiVersion", "monitoring.coreos.com/v1", "kind", "ServiceMonitorList", "metadata", Map.of()),
                Map.of("apiVersion", "monitoring.coreos.com/v1", "kind", "ServiceMonitorList",
                        "metadata", Map.of("resourceVersion", "123")));
    }

    @Test
    void monitorWithMissingMetadataIsUnknown() {
        Map<String, Object> monitor = monitor("matching", normalSpec());
        monitor.remove("metadata");
        respond(List.of(monitor));
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"30s", "1m30s", "1h2m3s4ms"})
    void validOrderedDurationsAreRetained(String interval) {
        respond(List.of(monitor("matching", spec(labels(), List.of(
                Map.of("port", "management", "interval", interval, "scrapeTimeout", "1ms"))))));
        var result = probe.probe(target);
        assertThat(result.readiness()).isEqualTo(ScrapeReadiness.SCRAPE_HEALTHY);
        assertThat(result.interval()).isEqualTo(interval);
        assertThat(result.scrapeTimeout()).isEqualTo("1ms");
    }

    @Test
    void absentOptionalDurationsRemainNull() {
        respond(List.of(monitor("matching", spec(labels(), List.of(Map.of("port", "management"))))));
        var result = probe.probe(target);
        assertThat(result.readiness()).isEqualTo(ScrapeReadiness.SCRAPE_HEALTHY);
        assertThat(result.interval()).isNull();
        assertThat(result.scrapeTimeout()).isNull();
    }

    @ParameterizedTest
    @MethodSource("invalidDurations")
    void invalidDurationsAreNotReturnedOrInterpretedAsConfiguration(Object duration) {
        respond(List.of(monitor("matching", spec(labels(), List.of(
                Map.of("port", "management", "interval", duration))))));
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    static Stream<Object> invalidDurations() {
        return Stream.of("0s", "-1s", "1s1m", "1s1s", "1.5s", "1ms".repeat(30),
                "token=" + CANARY, 30);
    }

    @Test
    void timeoutLongerThanIntervalIsUnknown() {
        respond(List.of(monitor("matching", spec(labels(), List.of(
                Map.of("port", "management", "interval", "10s", "scrapeTimeout", "30s"))))));
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    @ParameterizedTest
    @MethodSource("downObservations")
    void anyObservedZeroIsDownRatherThanHealthySeriesCount(int observed, int successful, int failed) {
        respond(List.of(monitor("matching", normalSpec())));
        when(provider.probeScrape(target)).thenReturn(observation(observed, successful, failed));
        var result = probe.probe(target);
        assertThat(result.readiness()).isEqualTo(ScrapeReadiness.SCRAPE_TARGET_DOWN);
        assertThat(result.serviceMonitorPresent()).isTrue();
        assertThat(result.detail()).doesNotContain(CANARY);
    }

    static Stream<Arguments> downObservations() {
        return Stream.of(Arguments.of(1, 0, 1), Arguments.of(2, 1, 1));
    }

    @ParameterizedTest
    @MethodSource("unusableObservations")
    void absentStaleMalformedOrFailedScrapeObservationIsUnknown(ScrapeObservation observation) {
        respond(List.of(monitor("matching", normalSpec())));
        when(provider.probeScrape(target)).thenReturn(observation);
        var result = probe.probe(target);
        assertThat(result.readiness()).isEqualTo(ScrapeReadiness.UNKNOWN);
        assertThat(result.detail()).doesNotContain(CANARY);
        assertThat(result.serviceMonitorPresent()).isTrue();
    }

    static Stream<ScrapeObservation> unusableObservations() {
        return Stream.of(null, ScrapeObservation.stale(), ScrapeObservation.unavailable(CANARY),
                new ScrapeObservation(MetricAvailability.AVAILABLE, 2, 2, 1, Instant.now(), CANARY),
                new ScrapeObservation(MetricAvailability.AVAILABLE, 0, 0, 0, Instant.now(), CANARY));
    }

    @Test
    void metricsDisabledDoesNotIssueScrapeProbe() {
        respond(List.of(monitor("matching", normalSpec())));
        when(provider.supported(target)).thenReturn(false);
        var result = probe.probe(target);
        assertThat(result.readiness()).isEqualTo(ScrapeReadiness.METRICS_DISABLED);
        assertThat(result.serviceMonitorPresent()).isTrue();
        verify(provider, never()).probeScrape(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void forbiddenListHasNoConfigurationOrRawDiagnostic(int status) {
        server.expect().get().withPath(LIST_PATH).andReturn(status,
                new StatusBuilder().withCode(status).withMessage(CANARY).build()).always();
        var result = probe.probe(target);
        assertThat(result.readiness()).isEqualTo(ScrapeReadiness.PERMISSION_DENIED);
        assertThat(result.serviceMonitorPresent()).isNull();
        assertThat(result.interval()).isNull();
        assertThat(result.scrapeTimeout()).isNull();
        assertThat(result.detail()).doesNotContain(CANARY);
        verifyNoInteractions(metrics, provider);
    }

    @Test
    void otherListFailureIsUnknownNotMissing() {
        server.expect().get().withPath(LIST_PATH).andReturn(404,
                new StatusBuilder().withCode(404).withMessage(CANARY).build()).always();
        assertUnknown(probe.probe(target));
        verifyNoInteractions(metrics, provider);
    }

    @Test
    void providerExceptionDetailsAreNotReturnedOrLogged() {
        respond(List.of(monitor("matching", normalSpec())));
        when(provider.probeScrape(target)).thenThrow(new IllegalStateException(CANARY));
        assertSafeFailureLogs(() -> {
            var result = probe.probe(target);
            assertThat(result.readiness()).isEqualTo(ScrapeReadiness.UNKNOWN);
            assertThat(result.detail()).doesNotContain(CANARY);
        });
    }

    @Test
    void infrastructureExceptionDetailsAreNotReturnedOrLogged() {
        when(infrastructure.resolve(target)).thenThrow(new IllegalStateException(CANARY));
        assertSafeFailureLogs(() -> assertUnknown(probe.probe(target)));
        verifyNoInteractions(inventory, metrics, provider);
    }

    private static void assertSafeFailureLogs(Runnable operation) {
        var logger = java.util.logging.Logger.getLogger(ServiceMonitorProbe.class.getName());
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
            operation.run();
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

    private static void assertUnknown(ServiceMonitorProbe.Result result) {
        assertThat(result.readiness()).isEqualTo(ScrapeReadiness.UNKNOWN);
        assertThat(result.serviceMonitorPresent()).isNull();
        assertThat(result.interval()).isNull();
        assertThat(result.scrapeTimeout()).isNull();
        assertThat(result.detail()).isNotBlank().doesNotContain(CANARY);
    }

    private void respond(List<Map<String, Object>> items) {
        respond(items, Map.of());
    }

    private void respond(List<Map<String, Object>> items, Map<String, Object> metadata) {
        server.expect().get().withPath(LIST_PATH).andReturn(200, Map.of(
                "apiVersion", "monitoring.coreos.com/v1", "kind", "ServiceMonitorList",
                "metadata", metadata, "items", items)).always();
    }

    private static Map<String, Object> monitor(String name, Map<String, Object> spec) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("apiVersion", "monitoring.coreos.com/v1");
        resource.put("kind", "ServiceMonitor");
        resource.put("metadata", Map.of("name", name, "namespace", NAMESPACE, "uid", "uid-" + name,
                "annotations", Map.of("diagnostic", CANARY)));
        resource.put("spec", spec);
        return resource;
    }

    private static Map<String, Object> normalSpec() { return spec(labels(), List.of(endpoint())); }
    private static Map<String, Object> labels() { return Map.of("matchLabels", Map.of("instance", "a")); }
    private static Map<String, Object> endpoint() {
        return Map.of("port", "management", "interval", "30s", "scrapeTimeout", "10s");
    }
    private static Map<String, Object> spec(Object selector, List<?> endpoints) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("selector", selector);
        spec.put("endpoints", endpoints);
        return spec;
    }

    private static ScrapeObservation observation(int observed, int successful, int failed) {
        return new ScrapeObservation(MetricAvailability.AVAILABLE, observed, successful, failed, Instant.now(), null);
    }

    private static Service service(String name, Map<String, String> labels) {
        return new ServiceBuilder().withNewMetadata().withName(name).withNamespace(NAMESPACE)
                .withUid("uid-" + name).withLabels(labels).endMetadata().withNewSpec()
                .addToSelector("pod-app", "keycloak-a").addNewPort().withName("management")
                .withPort(9000).withProtocol("TCP").endPort().endSpec().build();
    }

    private static Target target(boolean bound) {
        return new Target(TargetId.of("lab-a"), "Lab A", TargetType.KEYCLOAK, TargetEnvironment.DEV, true,
                new KeycloakTargetConfiguration("http://keycloak.invalid", "master", "ops", "keycloak-ref"),
                new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "approved-cluster", NAMESPACE,
                        "infra-ref", bound ? new KubernetesInstallationBinding("apps/v1", "Deployment", "keycloak-a", "root-a") : null),
                new ObservabilityTargetConfiguration("PROMETHEUS", null, "http://metrics.invalid", "metrics-ref", null, "NAMESPACE"),
                Map.of());
    }
}
