package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.kubernetes.api.model.apps.DeploymentListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.adapter.infrastructure.InfrastructureClientFactory;
import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.discovery.DetectionConfidence;
import io.github.keycloakmcp.discovery.ClusterApiCapabilities;
import io.github.keycloakmcp.discovery.ClusterApiCapabilities.ApiAvailability;
import io.github.keycloakmcp.discovery.EnvironmentDiscovery;
import io.github.keycloakmcp.discovery.EnvironmentInfo;
import io.github.keycloakmcp.discovery.RuntimeType;
import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
import io.github.keycloakmcp.target.InfrastructureTargetConfiguration;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.KubernetesInstallationBinding;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetResolver;
import io.github.keycloakmcp.target.TargetType;

@EnableKubernetesMockClient(crud = true)
class InventoryServiceMockTest {

    KubernetesMockServer server;
    KubernetesClient client;

    private InventoryService inventoryService;
    private TargetResolver targetResolver;
    private TargetAuthorizationService authz;
    private InfrastructureClientFactory clientFactory;
    private EnvironmentDiscovery environmentDiscovery;
    private ClusterClient clusterClient;
    private boolean configReadsForbidden;
    private boolean routeReadsForbidden;

    @BeforeEach
    void setUp() {
        io.github.keycloakmcp.testing.TypedKubernetesCrud.install(server);
        targetResolver = mock(TargetResolver.class);
        authz = mock(TargetAuthorizationService.class);
        clientFactory = mock(InfrastructureClientFactory.class);
        environmentDiscovery = mock(EnvironmentDiscovery.class);

        inventoryService = new InventoryService(targetResolver, authz, clientFactory, environmentDiscovery);

        clusterClient = mock(ClusterClient.class);
        when(clusterClient.kubernetes()).thenReturn(client);
        when(clusterClient.namespace()).thenReturn("rhbk");
        when(clientFactory.resolve(any())).thenReturn(Optional.of(clusterClient));

        when(environmentDiscovery.discover(any())).thenAnswer(invocation -> {
            Target target = invocation.getArgument(0);
            return observed(target.id().value(), RuntimeType.KUBERNETES, "v1.29.0",
                    target.infrastructureTypeOrNone(), ApiAvailability.NOT_SERVED, ApiAvailability.NOT_SERVED);
        });
    }

    @AfterEach
    void usesSharedDiscoveryInsteadOfImplicitWrapperDetection() throws InterruptedException {
        verify(clusterClient, never()).type();
        verify(clusterClient, never()).openshift();
        for (int i = 0, count = server.getRequestCount(); i < count; i++) {
            var request = server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isNotIn("/apis", "/version");
            if (configReadsForbidden) assertThat(request.getPath()).doesNotStartWith("/apis/config.openshift.io/");
            if (routeReadsForbidden) assertThat(request.getPath()).doesNotStartWith("/apis/route.openshift.io/");
        }
    }

    @Test
    void configuredOpenShiftWithObservedKubernetesDoesNotReadOpenShiftApis() {
        var deployment = deployment("keycloak", 3);
        when(targetResolver.require("target-a")).thenReturn(target("target-a", "rhbk",
                new KubernetesInstallationBinding("apps/v1", "Deployment", "keycloak", deployment.getMetadata().getUid()),
                InfrastructureType.OPENSHIFT));
        configReadsForbidden = true;
        routeReadsForbidden = true;

        var inventory = inventoryService.collect("target-a");

        assertThat(inventory.runtime()).isEqualTo("KUBERNETES");
        assertThat(inventory.discovery().configuredType()).isEqualTo(InfrastructureType.OPENSHIFT);
        assertThat(inventory.discovery().apiCapabilities().configV1()).isEqualTo(ApiAvailability.NOT_SERVED);
        assertThat(inventory.networking().complete()).isTrue();
        assertThat(inventory.warnings()).noneMatch(w -> "openshift-config".equals(w.resource()));
    }

    @Test
    void configuredKubernetesWithObservedConfigApiCollectsOpenShiftFactsWithoutRouteApi() {
        bind("target-a", deployment("keycloak", 3));
        var observed = observed("target-a", RuntimeType.OPENSHIFT, "v1.32.2", InfrastructureType.KUBERNETES,
                ApiAvailability.NOT_SERVED, ApiAvailability.SERVED);
        doReturn(observed).when(environmentDiscovery).discover(any());
        respondOpenShiftConfig();
        routeReadsForbidden = true;

        var inventory = inventoryService.collect("target-a");

        assertThat(inventory.discovery()).isSameAs(observed);
        assertThat(inventory.runtime()).isEqualTo("OPENSHIFT");
        assertThat(inventory.cluster().platform()).isEqualTo("AWS");
        assertThat(inventory.cluster().version()).isEqualTo("4.19.1");
        assertThat(inventory.networking().complete()).isTrue();
        assertThat(inventory.warnings()).noneMatch(w -> List.of("openshift-config", "infrastructure", "clusterversion")
                .contains(w.resource()));
    }

    @ParameterizedTest
    @EnumSource(value = ApiAvailability.class, names = {"UNKNOWN", "NOT_SERVED", "UNSUPPORTED_VERSION"})
    void observedOpenShiftWithoutSupportedConfigVersionRemainsPartial(ApiAvailability config) {
        bind("target-a", deployment("keycloak", 3));
        doReturn(observed("target-a", RuntimeType.OPENSHIFT,
                "v1.32.2", InfrastructureType.KUBERNETES, ApiAvailability.NOT_SERVED, config))
                .when(environmentDiscovery).discover(any());
        configReadsForbidden = true;
        routeReadsForbidden = true;

        var inventory = inventoryService.collect("target-a");

        assertThat(inventory.discovery().apiCapabilities().configV1()).isEqualTo(config);
        assertThat(inventory.warnings()).anyMatch(w -> "openshift-config".equals(w.resource()));
        assertThat(inventoryService.toEvidence(inventory)).noneMatch(e -> e.key().equals("cluster.platform")
                || e.key().equals("cluster.version"));
        assertThat(inventoryService.toEvidence(inventory)).anyMatch(e ->
                e.key().equals("infrastructure.collection.complete") && Boolean.FALSE.equals(e.value()));
        assertThat(inventoryService.toEvidence(inventory)).anyMatch(e -> e.key().equals("deployment.replicas"));
    }

    @Test
    void unknownDiscoveryDoesNotBecomeSuccessfulEmptyCoverage() {
        bind("target-a", deployment("keycloak", 3));
        var unknown = new EnvironmentInfo(RuntimeType.UNKNOWN, DetectionConfidence.UNKNOWN, "unknown", null,
                List.of("Cluster observation unavailable"), "target-a", null, null,
                InfrastructureType.KUBERNETES, ClusterApiCapabilities.unknown());
        doReturn(unknown).when(environmentDiscovery).discover(any());
        configReadsForbidden = true;
        routeReadsForbidden = true;

        var inventory = inventoryService.collect("target-a");

        assertThat(inventory.discovery()).isSameAs(unknown);
        assertThat(inventory.runtime()).isEqualTo("UNKNOWN");
        assertThat(inventory.networking().complete()).isFalse();
        assertThat(inventory.warnings()).anyMatch(w -> "discovery".equals(w.resource()));
        assertThat(inventoryService.toEvidence(inventory)).anyMatch(e -> e.key().equals("deployment.replicas"));
        assertThat(inventoryService.toEvidence(inventory)).noneMatch(e -> e.key().equals("keycloak.route.present"));
    }

    @Test
    void missingApiVersionPreservesObservedCapabilitiesAndIndependentConfigData() {
        bind("target-a", deployment("keycloak", 3));
        var observed = observed("target-a", RuntimeType.OPENSHIFT, null, InfrastructureType.KUBERNETES,
                ApiAvailability.NOT_SERVED, ApiAvailability.SERVED);
        doReturn(observed).when(environmentDiscovery).discover(any());
        respondOpenShiftConfig();

        var inventory = inventoryService.collect("target-a");

        assertThat(inventory.discovery()).isSameAs(observed);
        assertThat(inventory.cluster().version()).isEqualTo("4.19.1");
        assertThat(inventory.cluster().platform()).isEqualTo("AWS");
        assertThat(inventory.warnings()).anyMatch(w -> "cluster-api-version".equals(w.resource()));
        assertThat(inventoryService.toEvidence(inventory)).anyMatch(e ->
                e.key().equals("infrastructure.collection.complete") && Boolean.FALSE.equals(e.value()));
    }

    @ParameterizedTest
    @EnumSource(value = InfrastructureType.class, names = {"VM", "NONE"})
    void unsupportedOrDisabledInfrastructurePreservesDeclarationWithoutClientAccess(InfrastructureType configured) {
        when(targetResolver.require("no-cluster")).thenReturn(target("no-cluster", "rhbk", null, configured));
        environmentDiscovery = new EnvironmentDiscovery(mock(io.github.keycloakmcp.config.DiscoveryConfig.class), clientFactory);
        inventoryService = new InventoryService(targetResolver, authz, clientFactory, environmentDiscovery);

        var inventory = inventoryService.collect("no-cluster");

        assertThat(inventory.discovery().configuredType()).isEqualTo(configured);
        assertThat(inventory.discovery().runtime()).isEqualTo(RuntimeType.UNKNOWN);
        assertThat(inventory.discovery().apiCapabilities()).isEqualTo(ClusterApiCapabilities.unknown());
        assertThat(inventory.warnings()).anyMatch(w -> w.code().name().equals(
                configured == InfrastructureType.VM ? "NOT_SUPPORTED" : "NOT_CONFIGURED"));
        verifyNoInteractions(clientFactory);
        assertThat(server.getRequestCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-target", "wrong-namespace", "missing-namespace", "null"})
    void invalidDiscoveryScopeCannotBeUsedForInfrastructureReads(String invalid) {
        bind("target-a", deployment("keycloak", 3));
        EnvironmentInfo observation = switch (invalid) {
            case "wrong-target" -> observed("target-b", RuntimeType.OPENSHIFT, "v1.32.2",
                    InfrastructureType.KUBERNETES, ApiAvailability.SERVED, ApiAvailability.SERVED);
            case "wrong-namespace", "missing-namespace" -> new EnvironmentInfo(RuntimeType.OPENSHIFT,
                    DetectionConfidence.CONFIRMED, "openshift", invalid.equals("wrong-namespace") ? "foreign" : null,
                    List.of(), "target-a", "v1.32.2", "openshift", InfrastructureType.KUBERNETES,
                    new ClusterApiCapabilities(ApiAvailability.SERVED, ApiAvailability.SERVED));
            default -> null;
        };
        doReturn(observation).when(environmentDiscovery).discover(any());
        int before = server.getRequestCount();

        var inventory = inventoryService.collect("target-a");

        assertThat(inventory.runtime()).isEqualTo("UNKNOWN");
        assertThat(inventoryService.toEvidence(inventory)).noneMatch(e -> e.key().equals("deployment.replicas"));
        assertThat(server.getRequestCount()).isEqualTo(before);
    }

    private static EnvironmentInfo observed(String id, RuntimeType runtime, String version, InfrastructureType configured,
            ApiAvailability routes, ApiAvailability config) {
        String platform = runtime.name().toLowerCase(java.util.Locale.ROOT);
        return new EnvironmentInfo(runtime, DetectionConfidence.CONFIRMED, platform, "rhbk", List.of("mock"),
                id, version, platform, configured, new ClusterApiCapabilities(routes, config));
    }

    private void respondOpenShiftConfig() {
        server.expect().get().withPath("/apis/config.openshift.io/v1/infrastructures/cluster")
                .andReturn(200, Map.of("apiVersion", "config.openshift.io/v1", "kind", "Infrastructure",
                        "metadata", Map.of("name", "cluster", "uid", "cluster-uid"), "status", Map.of("platform", "AWS"))).always();
        server.expect().get().withPath("/apis/config.openshift.io/v1/clusterversions/version")
                .andReturn(200, Map.of("apiVersion", "config.openshift.io/v1", "kind", "ClusterVersion",
                        "metadata", Map.of("name", "version", "uid", "version-uid"),
                        "status", Map.of("desired", Map.of("version", "4.19.1")))).always();
    }

    @Test
    void collectsDeploymentReplicasAndEmitsEvidenceWithTargetId() {
        Deployment deployment = client.apps().deployments().inNamespace("rhbk").resource(new DeploymentBuilder()
                .withNewMetadata().withName("keycloak").withNamespace("rhbk")
                .addToLabels("app", "keycloak").endMetadata()
                .withNewSpec().withReplicas(3)
                .withNewSelector().addToMatchLabels("app", "keycloak").endSelector()
                .withNewTemplate().withNewMetadata().addToLabels("app", "keycloak").endMetadata()
                .withNewSpec().addNewContainer().withName("keycloak")
                .withNewResources()
                .addToRequests("cpu", new Quantity("500m"))
                .addToRequests("memory", new Quantity("1Gi"))
                .endResources()
                .endContainer().endSpec().endTemplate().endSpec()
                .withNewStatus().withReplicas(3).withReadyReplicas(3).withAvailableReplicas(3).endStatus()
                .build()).create();
        bind("target-a", deployment);
        ownedPod(deployment, "keycloak-0");
        client.services().inNamespace("rhbk").resource(new io.fabric8.kubernetes.api.model.ServiceBuilder()
                .withNewMetadata().withName("approved-service").withNamespace("rhbk").endMetadata()
                .withNewSpec().addToSelector("app", "keycloak").endSpec().build()).create();

        InfrastructureInventory inventory = inventoryService.collect("target-a");
        assertThat(inventory.targetId()).isEqualTo("target-a");
        assertThat(inventory.keycloak().desiredReplicas()).isEqualTo(3);
        assertThat(inventory.pods()).isNotEmpty();

        List<Evidence> evidence = inventoryService.toEvidence(inventory);
        assertThat(evidence).isNotEmpty();
        assertThat(evidence).allMatch(e -> "target-a".equals(e.targetId()));
        assertThat(evidence).anyMatch(e -> "deployment.replicas".equals(e.key()) && Integer.valueOf(3).equals(e.value()));
        assertThat(evidence).anyMatch(e -> "keycloak.workload.uid".equals(e.key()) && deployment.getMetadata().getUid().equals(e.value()));
        assertThat(inventory.networking().complete()).isTrue();
        assertThat(inventory.networking().services()).extracting(s -> s.name()).containsExactly("approved-service");
        assertThat(inventory.networking().routeOrIngressPresent()).isFalse();
        assertThat(evidence).anyMatch(e -> "keycloak.route.present".equals(e.key()) && Boolean.FALSE.equals(e.value()));
    }

    @Test
    void deniedNetworkingNeverEmitsAnAbsentRouteFindingInput() {
        bind("target-a", deployment("keycloak", 3));
        server.expect().get().withPath("/api/v1/namespaces/rhbk/services?limit=501")
                .andReturn(403, new io.fabric8.kubernetes.api.model.StatusBuilder().withCode(403).withReason("Forbidden").build()).always();
        var inventory = inventoryService.collect("target-a");
        assertThat(inventory.networking().complete()).isFalse();
        assertThat(inventoryService.toEvidence(inventory)).noneMatch(e -> e.category().equals("networking"));
        assertThat(inventoryService.toEvidence(inventory)).anyMatch(e -> e.key().equals("collection.warning.networking"));
    }

    @Test
    void unboundTargetDoesNotOpenInfrastructureClientOrInventEvidence() {
        when(targetResolver.require("unbound")).thenReturn(target("unbound", "rhbk", null));
        InfrastructureInventory inventory = inventoryService.collect("unbound");
        assertUnavailable(inventory, "BINDING_REQUIRED");
        verifyNoInteractions(clientFactory, environmentDiscovery);
    }

    @Test
    void isolatesTwoInstallationsWithIdenticalLabelsInSameNamespace() {
        Deployment a = deployment("keycloak-a", 2);
        Deployment b = deployment("keycloak-b", 4);
        bind("target-a", a);
        bind("target-b", b);
        ownedPod(a, "pod-a");
        ownedPod(b, "pod-b");
        // A matching label without controller ownership does not establish identity.
        client.pods().inNamespace("rhbk").resource(new PodBuilder().withNewMetadata()
                .withName("unrelated").withNamespace("rhbk").addToLabels("app", "keycloak")
                .endMetadata().build()).create();
        for (String suffix : List.of("a", "b")) {
            var inventory = inventoryService.collect("target-" + suffix);
            assertThat(inventory.keycloak().name()).isEqualTo("keycloak-" + suffix);
            assertThat(inventory.keycloak().desiredReplicas()).isEqualTo(suffix.equals("a") ? 2 : 4);
            assertThat(inventory.pods()).extracting(p -> p.name()).containsExactly("pod-" + suffix);
            assertThat(inventoryService.toEvidence(inventory)).allMatch(e -> e.targetId().equals("target-" + suffix));
        }
    }

    @Test
    void rejectsRecreatedResourceWithSameNameAndDifferentUid() {
        Deployment original = deployment("keycloak", 2);
        bind("target-a", original);
        client.apps().deployments().inNamespace("rhbk").withName("keycloak").delete();
        Deployment replacement = deployment("keycloak", 9);
        assertThat(replacement.getMetadata().getUid()).isNotEqualTo(original.getMetadata().getUid());
        assertUnavailable(inventoryService.collect("target-a"), "BINDING_MISMATCH");
    }

    @Test
    void missingInstallationNeverFallsBackToAnotherDeployment() {
        deployment("keycloak-other", 5);
        when(targetResolver.require("missing")).thenReturn(target("missing", "rhbk",
                new KubernetesInstallationBinding("apps/v1", "Deployment", "keycloak-missing", "expected-uid")));
        assertUnavailable(inventoryService.collect("missing"), "RESOURCE_NOT_FOUND");
    }

    @Test
    void boundStatefulSetUsesDirectPodOwnership() {
        var sts = client.apps().statefulSets().inNamespace("rhbk").resource(new StatefulSetBuilder()
                .withNewMetadata().withName("keycloak-sts").withNamespace("rhbk").endMetadata()
                .withNewSpec().withReplicas(2).withNewSelector().addToMatchLabels("app", "keycloak").endSelector()
                .withNewTemplate().withNewMetadata().addToLabels("app", "keycloak").endMetadata()
                .withNewSpec().addNewContainer().withName("keycloak").endContainer().endSpec()
                .endTemplate().endSpec().build()).create();
        when(targetResolver.require("sts")).thenReturn(target("sts", "rhbk",
                new KubernetesInstallationBinding("apps/v1", "StatefulSet", "keycloak-sts", sts.getMetadata().getUid())));
        client.pods().inNamespace("rhbk").resource(new PodBuilder().withNewMetadata()
                .withName("keycloak-sts-0").withNamespace("rhbk").addNewOwnerReference()
                .withApiVersion("apps/v1").withKind("StatefulSet").withName("keycloak-sts")
                .withUid(sts.getMetadata().getUid()).withController(true).endOwnerReference().endMetadata().build()).create();
        var inventory = inventoryService.collect("sts");
        assertThat(inventory.keycloak().uid()).isEqualTo(sts.getMetadata().getUid());
        assertThat(inventory.pods()).extracting(p -> p.name()).containsExactly("keycloak-sts-0");
    }

    @Test
    void operatorCrWithoutOwnedWorkloadDoesNotAdoptLookalikeOrInventHealth() {
        var cr = keycloakCr();
        deployment("keycloak", 7);
        bindCr(cr);
        var inventory = inventoryService.collect("operator");
        assertThat(inventory.keycloak().desiredReplicas()).isEqualTo(3);
        assertThat(inventory.keycloak().readyReplicas()).isEqualTo(-1);
        assertThat(inventoryService.toEvidence(inventory)).noneMatch(e ->
                List.of("pods", "topology", "scheduling", "autoscaling", "disruption", "resources", "probes").contains(e.category()));
    }

    @Test
    void malformedOperatorReplicaValuesNeverBecomeValidAssessmentCounts() {
        var cr = keycloakCr();
        bindCr(cr);
        String path = "/apis/k8s.keycloak.org/v2alpha1/namespaces/rhbk/keycloaks/rhbk";
        for (Number value : List.of(-1, 2.5, 4_294_967_299L)) {
            cr.setAdditionalProperty("spec", Map.of("instances", value));
            server.expect().get().withPath(path).andReturn(200, cr).once();
            var inventory = inventoryService.collect("operator");
            assertThat(inventory.keycloak().desiredReplicas()).isEqualTo(-1);
            assertThat(inventoryService.toEvidence(inventory)).noneMatch(e -> e.key().equals("deployment.replicas"));
        }
    }

    @Test
    void operatorCrRejectsAmbiguousOwnedWorkloads() {
        var cr = keycloakCr();
        bindCr(cr);
        for (String name : List.of("owned-a", "owned-b")) {
            var deployment = deployment(name, 3);
            client.apps().deployments().inNamespace("rhbk").withName(name).edit(d -> new DeploymentBuilder(d)
                    .editMetadata().addNewOwnerReference().withApiVersion(cr.getApiVersion())
                    .withKind("Keycloak").withName(cr.getMetadata().getName()).withUid(cr.getMetadata().getUid())
                    .withController(true).endOwnerReference().endMetadata().build());
        }
        assertUnavailable(inventoryService.collect("operator"), "AMBIGUOUS_RESOURCE");
    }

    private GenericKubernetesResource keycloakCr() {
        var definition = new ResourceDefinitionContext.Builder().withGroup("k8s.keycloak.org")
                .withVersion("v2alpha1").withPlural("keycloaks").withNamespaced(true).build();
        var cr = new GenericKubernetesResource();
        cr.setApiVersion("k8s.keycloak.org/v2alpha1");
        cr.setKind("Keycloak");
        cr.setMetadata(new ObjectMetaBuilder().withName("rhbk").withNamespace("rhbk").build());
        cr.setAdditionalProperty("spec", Map.of("instances", 3));
        return client.genericKubernetesResources(definition).inNamespace("rhbk").resource(cr).create();
    }

    @Test
    void deniedInstallationIsUnknownAndDoesNotFallBack() {
        when(targetResolver.require("denied")).thenReturn(target("denied", "rhbk",
                new KubernetesInstallationBinding("apps/v1", "Deployment", "denied", "expected-uid")));
        server.expect().get().withPath("/apis/apps/v1/namespaces/rhbk/deployments/denied")
                .andReturn(403, new io.fabric8.kubernetes.api.model.StatusBuilder().withCode(403).withReason("Forbidden").build()).always();
        assertUnavailable(inventoryService.collect("denied"), "PERMISSION_DENIED");
    }

    @Test
    void deniedPodsDoNotBecomeZeroPodEvidence() {
        bind("target-a", deployment("keycloak", 3));
        server.expect().get().withPath("/api/v1/namespaces/rhbk/pods?limit=501")
                .andReturn(403, new io.fabric8.kubernetes.api.model.StatusBuilder().withCode(403).withReason("Forbidden").build()).always();
        var inventory = inventoryService.collect("target-a");
        assertThat(inventory.warnings()).anyMatch(w -> w.resource().equals("pods") && w.code().name().equals("PERMISSION_DENIED"));
        assertThat(inventoryService.toEvidence(inventory)).noneMatch(e -> List.of("pods", "topology").contains(e.category()));
        assertThat(inventoryService.toEvidence(inventory)).anyMatch(e -> e.key().equals("deployment.replicas"));
    }

    @Test
    void deniedApiMessagesCannotLeakProviderCanaries() {
        bind("target-a", deployment("keycloak", 3));
        server.expect().get().withPath("/api/v1/namespaces/rhbk/pods?limit=501")
                .andReturn(403, new io.fabric8.kubernetes.api.model.StatusBuilder().withCode(403)
                        .withReason("Forbidden").withMessage("raw-provider-canary credential-private https://private.invalid").build()).always();
        var inventory = inventoryService.collect("target-a");
        assertThat(inventory.toString()).doesNotContain("raw-provider-canary", "credential-private", "https://private.invalid");
        assertThat(inventoryService.toEvidence(inventory)).anyMatch(e ->
                e.key().equals("infrastructure.collection.complete") && Boolean.FALSE.equals(e.value()));
    }

    @Test
    void missingPodContainerStatusDoesNotBecomeHealthyZeroRestarts() {
        var deployment = deployment("keycloak", 3);
        bind("target-a", deployment);
        ownedPod(deployment, "missing-status");
        var inventory = inventoryService.collect("target-a");
        assertThat(inventory.pods()).extracting(p -> p.restartCount()).containsExactly(-1);
        assertThat(inventory.warnings()).anyMatch(w -> "pods".equals(w.resource()));
        assertThat(inventoryService.toEvidence(inventory)).noneMatch(e ->
                e.key().equals("keycloak.pods.restartCount") || e.key().equals("keycloak.pods.oomKilledCount"));
        assertThat(inventoryService.toEvidence(inventory)).anyMatch(e -> e.key().equals("deployment.replicas"));
    }

    @Test
    void observedZeroRestartsAndFalseReadinessArePreserved() {
        var deployment = deployment("keycloak", 3);
        bind("target-a", deployment);
        ownedPod(deployment, "observed-status");
        client.pods().inNamespace("rhbk").withName("observed-status").edit(p -> new PodBuilder(p)
                .withNewStatus().addNewCondition().withType("Ready").withStatus("False").endCondition()
                .addNewContainerStatus().withName("keycloak").withRestartCount(0).withReady(false)
                .withNewState().withNewWaiting().withReason("ContainerCreating").endWaiting().endState()
                .endContainerStatus().endStatus().build());
        var inventory = inventoryService.collect("target-a");
        assertThat(inventory.warnings()).noneMatch(w -> "pods".equals(w.resource()));
        assertThat(inventoryService.toEvidence(inventory)).anyMatch(e ->
                e.key().equals("keycloak.pods.restartCount") && Long.valueOf(0).equals(e.value()));
        assertThat(inventoryService.toEvidence(inventory)).anyMatch(e ->
                e.key().equals("keycloak.pods.ready") && Long.valueOf(0).equals(e.value()));
    }

    @Test
    void unlabeledNodesAreUnknownZonesNotObservedZeroZones() {
        bind("target-a", deployment("keycloak", 3));
        client.nodes().resource(new io.fabric8.kubernetes.api.model.NodeBuilder()
                .withNewMetadata().withName("node-without-zone").endMetadata().build()).create();
        var inventory = inventoryService.collect("target-a");
        assertThat(inventory.cluster().nodeCount()).isEqualTo(1);
        assertThat(inventory.cluster().zoneCount()).isEqualTo(-1);
        assertThat(inventory.warnings()).anyMatch(w -> "node-zones".equals(w.resource()));
        assertThat(inventoryService.toEvidence(inventory)).noneMatch(e ->
                e.key().equals("cluster.zones.count") || e.key().equals("keycloak.topology.singleZoneConcentration"));
    }

    @Test
    void continuedNodeListIsPartialNotEvidenceOfZeroNodes() {
        bind("target-a", deployment("keycloak", 3));
        server.expect().get().withPath("/api/v1/nodes?limit=501")
                .andReturn(200, new io.fabric8.kubernetes.api.model.NodeListBuilder()
                        .withNewMetadata().withContinue("opaque-next-page").endMetadata().build()).always();
        var inventory = inventoryService.collect("target-a");
        assertThat(inventory.warnings()).anyMatch(w -> "nodes".equals(w.resource()));
        assertThat(inventoryService.toEvidence(inventory)).noneMatch(e -> e.key().equals("cluster.nodes.count"));
    }

    @Test
    void missingInstallationPreservesIndependentClusterObservations() {
        when(targetResolver.require("missing")).thenReturn(target("missing", "rhbk",
                new KubernetesInstallationBinding("apps/v1", "Deployment", "absent", "expected-uid")));
        var inventory = inventoryService.collect("missing");
        assertUnavailable(inventory, "RESOURCE_NOT_FOUND");
        assertThat(inventoryService.toEvidence(inventory)).anyMatch(e ->
                e.key().equals("cluster.version") && "v1.29.0".equals(e.value()));
        assertThat(inventoryService.toEvidence(inventory)).anyMatch(e ->
                e.key().equals("cluster.nodes.count") && Integer.valueOf(0).equals(e.value()));
    }

    @Test
    void policiesRequireFullWorkloadAssociationNotNameOrPartialLabelOverlap() {
        bind("target-a", deployment("keycloak", 3));
        client.autoscaling().v2().horizontalPodAutoscalers().inNamespace("rhbk").resource(
                new io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscalerBuilder()
                        .withNewMetadata().withName("foreign-hpa").withNamespace("rhbk").endMetadata()
                        .withNewSpec().withMaxReplicas(8).withNewScaleTargetRef().withName("keycloak")
                        .withKind("StatefulSet").withApiVersion("apps/v1").endScaleTargetRef().endSpec().build()).create();
        client.policy().v1().podDisruptionBudget().inNamespace("rhbk").resource(
                new io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetBuilder()
                        .withNewMetadata().withName("foreign-pdb").withNamespace("rhbk").endMetadata()
                        .withNewSpec().withNewSelector().addToMatchLabels("app", "keycloak")
                        .addToMatchLabels("installation", "other").endSelector().endSpec().build()).create();
        var inventory = inventoryService.collect("target-a");
        assertThat(inventory.hpa().present()).isFalse();
        assertThat(inventory.pdb().present()).isFalse();
    }

    private void bindCr(GenericKubernetesResource cr) {
        when(targetResolver.require("operator")).thenReturn(target("operator", "rhbk",
                new KubernetesInstallationBinding(cr.getApiVersion(), "Keycloak", cr.getMetadata().getName(), cr.getMetadata().getUid())));
    }

    private Deployment deployment(String name, int replicas) {
        return client.apps().deployments().inNamespace("rhbk").resource(new DeploymentBuilder()
                .withNewMetadata().withName(name).withNamespace("rhbk").endMetadata()
                .withNewSpec().withReplicas(replicas).withNewSelector().addToMatchLabels("app", "keycloak").endSelector()
                .withNewTemplate().withNewMetadata().addToLabels("app", "keycloak").endMetadata()
                .withNewSpec().addNewContainer().withName("keycloak").endContainer().endSpec()
                .endTemplate().endSpec().build()).create();
    }

    private void bind(String id, Deployment deployment) {
        when(targetResolver.require(id)).thenReturn(target(id, "rhbk", new KubernetesInstallationBinding(
                "apps/v1", "Deployment", deployment.getMetadata().getName(), deployment.getMetadata().getUid())));
    }

    private void ownedPod(Deployment deployment, String name) {
        var rs = client.apps().replicaSets().inNamespace("rhbk").resource(new ReplicaSetBuilder()
                .withNewMetadata().withName(name + "-rs").withNamespace("rhbk").addNewOwnerReference()
                .withApiVersion("apps/v1").withKind("Deployment").withName(deployment.getMetadata().getName())
                .withUid(deployment.getMetadata().getUid()).withController(true).endOwnerReference().endMetadata().build()).create();
        client.pods().inNamespace("rhbk").resource(new PodBuilder().withNewMetadata()
                .withName(name).withNamespace("rhbk").addToLabels("app", "keycloak").addNewOwnerReference()
                .withApiVersion("apps/v1").withKind("ReplicaSet").withName(rs.getMetadata().getName())
                .withUid(rs.getMetadata().getUid()).withController(true).endOwnerReference().endMetadata()
                .withNewSpec().withNodeName("node-a").endSpec()
                .withNewStatus().addNewCondition().withType("Ready").withStatus("True").endCondition().endStatus()
                .build()).create();
    }

    private void assertUnavailable(InfrastructureInventory inventory, String code) {
        assertThat(inventory.warnings()).anyMatch(w -> w.code().name().equals(code));
        assertThat(inventoryService.toEvidence(inventory)).allMatch(e ->
                List.of("collection", "runtime", "cluster").contains(e.category()));
        assertThat(inventory.pods()).isEmpty();
    }

    @Test
    void targetWithoutInfrastructureReturnsNotConfiguredWarning() {
        Target target = new Target(
                TargetId.of("no-infra"),
                "No Infra",
                TargetType.KEYCLOAK,
                TargetEnvironment.DEV,
                true,
                new KeycloakTargetConfiguration("http://kc", "master", "c", "lab-a"),
                null,
                null,
                Map.of());
        when(targetResolver.require("no-infra")).thenReturn(target);

        InfrastructureInventory inventory = inventoryService.collect("no-infra");
        assertThat(inventory.warnings()).isNotEmpty();
        assertThat(inventory.warnings().get(0).code().name()).isEqualTo("NOT_CONFIGURED");
    }

    private static Target target(String id, String namespace, KubernetesInstallationBinding binding) {
        return target(id, namespace, binding, InfrastructureType.KUBERNETES);
    }

    private static Target target(String id, String namespace, KubernetesInstallationBinding binding, InfrastructureType configured) {
        return new Target(
                TargetId.of(id),
                id,
                TargetType.KEYCLOAK,
                TargetEnvironment.PRD,
                true,
                new KeycloakTargetConfiguration("http://kc", "master", "c", "lab-a"),
                new InfrastructureTargetConfiguration(configured, "c1", namespace, "infra-a", binding),
                null,
                Map.of());
    }
}
