package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.domain.inventory.*;
import io.github.keycloakmcp.discovery.ClusterApiCapabilities;
import io.github.keycloakmcp.discovery.ClusterApiCapabilities.ApiAvailability;

@EnableKubernetesMockClient(crud = true)
class InstallationNetworkingCollectorTest {
    KubernetesMockServer server;
    KubernetesClient client;
    ClusterClient cluster;
    KeycloakWorkloadInfo root;
    List<CollectionWarning> warnings;
    ClusterApiCapabilities capabilities;
    boolean routeReadsForbidden;
    InstallationNetworkingCollector collector = new InstallationNetworkingCollector();

    @BeforeEach
    void setup() {
        io.github.keycloakmcp.testing.TypedKubernetesCrud.install(server);
        client.getConfiguration().setNamespace("iam");
        cluster = mock(ClusterClient.class);
        when(cluster.kubernetes()).thenReturn(client);
        capabilities = new ClusterApiCapabilities(ApiAvailability.NOT_SERVED, ApiAvailability.NOT_SERVED);
        warnings = new ArrayList<>();
        root = deployment("a");
    }

    @AfterEach
    void neverRediscoversCapabilitiesOrUsesConfiguredPlatformWrapper() throws InterruptedException {
        verify(cluster, never()).type();
        verify(cluster, never()).openshift();
        for (int i = 0, count = server.getRequestCount(); i < count; i++) {
            var request = server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isNotIn("/apis", "/version");
            if (routeReadsForbidden) assertThat(request.getPath()).doesNotStartWith("/apis/route.openshift.io/");
        }
    }

    @Test
    void unknownRouteCapabilityPreservesIngressButCannotProveCompleteNetworking() {
        service("svc-a", Map.of("instance", "a"));
        ingress("one", "a.example.test", "svc-a", 80, false);
        capabilities = ClusterApiCapabilities.unknown();
        // A route response exists but must not be queried without observed v1 support.
        server.expect().get().withPath("/apis/route.openshift.io/v1/namespaces/iam/routes?limit=501")
                .andReturn(500, "unexpected-route-read").always();

        var result = collector.collect(cluster, root, capabilities, warnings);

        assertThat(result.complete()).isFalse();
        assertThat(result.services()).extracting(NetworkingInfo.ServiceRef::name).containsExactly("svc-a");
        assertThat(result.exposures()).extracting(NetworkingInfo.Exposure::kind).containsExactly("Ingress");
        assertThat(warnings).anyMatch(w -> w.code() == CollectionWarning.WarningCode.COLLECTION_FAILED);
        assertThat(warnings).noneMatch(w -> w.code() == CollectionWarning.WarningCode.API_UNAVAILABLE);
        assertNoRouteReads();
    }

    @Test
    void unsupportedRouteVersionSkipsV1ReadsAndCannotProveAbsence() {
        service("svc-a", Map.of("instance", "a"));
        capabilities = new ClusterApiCapabilities(ApiAvailability.UNSUPPORTED_VERSION, ApiAvailability.SERVED);

        var result = collector.collect(cluster, root, capabilities, warnings);

        assertThat(result.complete()).isFalse();
        assertThat(result.services()).hasSize(1);
        assertThat(result.exposures()).isEmpty();
        assertThat(warnings).anyMatch(w -> w.code() == CollectionWarning.WarningCode.NOT_SUPPORTED);
        assertNoRouteReads();
    }

    @Test
    void configApiSupportAloneDoesNotImplyRouteSupport() {
        service("svc-a", Map.of("instance", "a"));
        capabilities = new ClusterApiCapabilities(ApiAvailability.NOT_SERVED, ApiAvailability.SERVED);

        var result = collector.collect(cluster, root, capabilities, warnings);

        assertThat(result.complete()).isTrue();
        assertThat(result.routeOrIngressPresent()).isFalse();
        assertThat(warnings).isEmpty();
        assertNoRouteReads();
    }

    @Test
    void routeSupportDoesNotRequireConfigApiOrOpenShiftClientAdaptation() {
        service("svc-a", Map.of("instance", "a"));
        openshift().routes().inNamespace("iam").resource(new RouteBuilder().withNewMetadata().withName("route")
                .withNamespace("iam").endMetadata().withNewSpec().withHost("a.example.test")
                .withNewTo().withKind("Service").withName("svc-a").endTo().endSpec().build()).create();
        capabilities = new ClusterApiCapabilities(ApiAvailability.SERVED, ApiAvailability.NOT_SERVED);

        var result = collector.collect(cluster, root, capabilities, warnings);

        assertThat(result.complete()).isTrue();
        assertThat(result.exposures()).extracting(NetworkingInfo.Exposure::kind).containsExactly("Route");
        assertThat(warnings).isEmpty();
    }

    @Test
    void existingNetworkingWarningCannotBecomeCompleteWhenNoNewWarningIsAdded() {
        service("svc-a", Map.of("instance", "a"));
        warnings.add(new CollectionWarning(CollectionWarning.WarningCode.COLLECTION_FAILED, "networking", null));

        var result = collector.collect(cluster, root, capabilities, warnings);

        assertThat(result.complete()).isFalse();
        assertThat(result.services()).hasSize(1);
        assertThat(warnings).hasSize(1);
    }

    private void assertNoRouteReads() {
        routeReadsForbidden = true;
    }

    @Test
    void sharedIngressOnlyReturnsBackendPathsForTheBoundInstallation() {
        var other = deployment("b");
        service("svc-a", Map.of("instance", "a"));
        service("svc-b", Map.of("instance", "b"));
        ingress("shared", "a.example.test", "svc-a", 80, false);
        client.network().v1().ingresses().inNamespace("iam").withName("shared").edit(i -> new IngressBuilder(i)
                .editSpec().addToRules(rule("b.example.test", "svc-b", 80)).endSpec().build());
        var a = collector.collect(cluster, root, capabilities, warnings);
        var b = collector.collect(cluster, other, capabilities, new ArrayList<>());
        assertThat(a.complete()).isTrue();
        assertThat(a.exposures()).extracting(NetworkingInfo.Exposure::host).containsExactly("a.example.test");
        assertThat(b.exposures()).extracting(NetworkingInfo.Exposure::host).containsExactly("b.example.test");
        assertThat(a.services()).extracting(NetworkingInfo.ServiceRef::name).containsExactly("svc-a");
    }

    @Test
    void commonSelectorAcrossWorkloadTemplatesIsAmbiguousEvenWithoutRunningPods() {
        deployment("b");
        service("shared", Map.of("app", "keycloak"));
        ingress("exposure", "shared.example.test", "shared", 80, false);
        assertGap("AMBIGUOUS_RESOURCE");
    }

    @Test
    void selectorMatchingAnUnownedPodIsNotExclusive() {
        service("svc-a", Map.of("instance", "a"));
        client.pods().inNamespace("iam").resource(new PodBuilder().withNewMetadata().withName("foreign")
                .withNamespace("iam").addToLabels("instance", "a").endMetadata().build()).create();
        assertGap("AMBIGUOUS_RESOURCE");
    }

    @Test
    void multipleHostsHaveNoFirstHostAndPreservePerExposureTlsConfiguration() {
        service("svc-a", Map.of("instance", "a"));
        ingress("one", "a.example.test", "svc-a", 80, true);
        ingress("two", "second.example.test", "svc-a", 80, false);
        var result = collector.collect(cluster, root, capabilities, warnings);
        assertThat(result.host()).isNull();
        assertThat(result.exposures()).hasSize(2);
        assertThat(result.tlsEnabled()).isFalse();
        assertThat(result.exposures()).extracting(NetworkingInfo.Exposure::tlsConfigured).containsExactly(true, false);
    }

    @Test
    void unrelatedTlsHostDoesNotClaimTlsForAssociatedHost() {
        service("svc-a", Map.of("instance", "a"));
        ingress("one", "a.example.test", "svc-a", 80, false);
        client.network().v1().ingresses().inNamespace("iam").withName("one").edit(i -> new IngressBuilder(i)
                .editSpec().addNewTl().addToHosts("other.example.test").withSecretName("not-read").endTl().endSpec().build());
        assertThat(collector.collect(cluster, root, capabilities, warnings).tlsEnabled()).isFalse();
    }

    @Test
    void wrongIngressServicePortRemainsAnExplicitGap() {
        service("svc-a", Map.of("instance", "a"));
        ingress("one", "a.example.test", "svc-a", 9999, false);
        assertGap("RESOURCE_NOT_FOUND");
    }

    @Test
    void namedIngressPortResolvesServicePort() {
        service("svc-a", Map.of("instance", "a"));
        ingress("one", "a.example.test", "svc-a", 80, false);
        client.network().v1().ingresses().inNamespace("iam").withName("one").edit(i -> {
            i.getSpec().getRules().getFirst().getHttp().getPaths().getFirst().getBackend().getService()
                    .setPort(new ServiceBackendPortBuilder().withName("http").build());
            return i;
        });
        assertThat(collector.collect(cluster, root, capabilities, warnings).routeOrIngressPresent()).isTrue();
        assertThat(warnings).isEmpty();
    }

    @Test
    void servicePermissionDeniedIsNotAnEmptySuccessfulInventory() {
        server.expect().get().withPath("/api/v1/namespaces/iam/services?limit=501")
                .andReturn(403, new StatusBuilder().withCode(403).withMessage("do-not-export-server-details").build()).always();
        assertGap("PERMISSION_DENIED");
        assertThat(warnings.toString()).doesNotContain("do-not-export-server-details");
    }

    @Test
    void paginatedServiceListIsNotEvidenceOfAbsence() {
        server.expect().get().withPath("/api/v1/namespaces/iam/services?limit=501")
                .andReturn(200, new ServiceListBuilder().withNewMetadata().withContinue("next-page").endMetadata().build()).always();
        assertGap("COLLECTION_FAILED");
    }

    @Test
    void selectorlessServiceIsUnsupportedRatherThanGuessedByName() {
        service("keycloak-a", Map.of());
        assertGap("NOT_SUPPORTED");
    }

    @Test
    void routeUsesExactServiceBackendAndNeverExportsTlsKeyMaterial() throws Exception {
        service("svc-a", Map.of("instance", "a"));
        var oc = openshift();
        oc.routes().inNamespace("iam").resource(new RouteBuilder().withNewMetadata().withName("unrelated-name")
                .withNamespace("iam").endMetadata().withNewSpec().withHost("a.example.test")
                .withNewTo().withKind("Service").withName("svc-a").endTo()
                .withNewTls().withTermination("edge").withKey("private-key-fixture-never-export").withCertificate("certificate-fixture-never-export")
                .endTls().endSpec().build()).create();
        var result = collector.collect(cluster, root, capabilities, warnings);
        assertThat(result.complete()).isTrue();
        assertThat(result.exposures()).extracting(NetworkingInfo.Exposure::kind).containsExactly("Route");
        assertThat(result.tlsEnabled()).isTrue();
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result))
                .doesNotContain("private-key-fixture", "certificate-fixture", "annotations");
    }

    @Test
    void routeWithForeignAlternateBackendIsNotAssignedExclusively() {
        deployment("b");
        service("svc-a", Map.of("instance", "a"));
        service("svc-b", Map.of("instance", "b"));
        openshift().routes().inNamespace("iam").resource(new RouteBuilder().withNewMetadata().withName("mixed")
                .withNamespace("iam").endMetadata().withNewSpec().withHost("mixed.example.test")
                .withNewTo().withKind("Service").withName("svc-a").endTo()
                .addNewAlternateBackend().withKind("Service").withName("svc-b").withWeight(0).endAlternateBackend()
                .endSpec().build()).create();
        assertGap("AMBIGUOUS_RESOURCE");
    }

    @Test
    void changedWorkloadUidIsRejectedDuringNetworkingCollection() {
        client.apps().deployments().inNamespace("iam").withName("keycloak-a").delete();
        deployment("a");
        assertGap("BINDING_MISMATCH");
    }

    @Test
    void defaultBackendWithoutHostDoesNotInventHostnameOrTls() {
        service("svc-a", Map.of("instance", "a"));
        client.network().v1().ingresses().inNamespace("iam").resource(new IngressBuilder()
                .withNewMetadata().withName("default").withNamespace("iam").endMetadata()
                .withNewSpec().withNewDefaultBackend().withNewService().withName("svc-a")
                .withNewPort().withNumber(80).endPort().endService().endDefaultBackend().endSpec().build()).create();
        var result = collector.collect(cluster, root, capabilities, warnings);
        assertThat(result.complete()).isTrue();
        assertThat(result.host()).isNull();
        assertThat(result.exposures().getFirst().tlsConfigured()).isNull();
    }

    @Test
    void foreignNamespacePayloadIsRejected() {
        server.expect().get().withPath("/api/v1/namespaces/iam/services?limit=501")
                .andReturn(200, new ServiceListBuilder().addNewItem().withNewMetadata()
                        .withName("foreign-private").withUid("other-uid").withNamespace("other")
                        .endMetadata().endItem().build()).always();
        assertGap("COLLECTION_FAILED");
        assertThat(warnings.toString()).doesNotContain("foreign-private");
    }

    @Test
    void deniedRouteApiPreservesObservedIngressButMarksCollectionPartial() {
        service("svc-a", Map.of("instance", "a"));
        ingress("one", "a.example.test", "svc-a", 80, false);
        openshift();
        server.expect().get().withPath("/apis/route.openshift.io/v1/namespaces/iam/routes?limit=501")
                .andReturn(403, new StatusBuilder().withCode(403).build()).always();
        var result = collector.collect(cluster, root, capabilities, warnings);
        assertThat(result.complete()).isFalse();
        assertThat(result.exposures()).hasSize(1);
        assertThat(warnings).anyMatch(w -> w.code() == CollectionWarning.WarningCode.PERMISSION_DENIED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-items", "missing-kind", "missing-version", "wrong-kind", "wrong-version", "missing-metadata", "remaining"})
    void malformedRawIngressEnvelopeIsNotSuccessfulAbsence(String malformed) throws Exception {
        service("svc-a", Map.of("instance", "a"));
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var body = json.createObjectNode().put("apiVersion", "networking.k8s.io/v1").put("kind", "IngressList");
        body.putObject("metadata");
        body.putArray("items");
        switch (malformed) {
            case "missing-items" -> body.remove("items");
            case "missing-kind" -> body.remove("kind");
            case "missing-version" -> body.remove("apiVersion");
            case "wrong-kind" -> body.put("kind", "ServiceList");
            case "wrong-version" -> body.put("apiVersion", "extensions/v1beta1");
            case "missing-metadata" -> body.remove("metadata");
            case "remaining" -> ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("metadata")).put("remainingItemCount", 1);
            default -> throw new AssertionError(malformed);
        }
        server.expect().get().withPath("/apis/networking.k8s.io/v1/namespaces/iam/ingresses?limit=501")
                .andReturn(200, json.writeValueAsString(body)).always();
        assertGap("COLLECTION_FAILED");
    }

    @Test void rawRouteWithoutItemsPreservesIngressButRemainsPartial() {
        service("svc-a", Map.of("instance", "a"));
        ingress("one", "a.example.test", "svc-a", 80, false);
        openshift();
        server.expect().get().withPath("/apis/route.openshift.io/v1/namespaces/iam/routes?limit=501")
                .andReturn(200, "{\"apiVersion\":\"route.openshift.io/v1\",\"kind\":\"RouteList\",\"metadata\":{}}").always();
        var result = collector.collect(cluster, root, capabilities, warnings);
        assertThat(result.complete()).isFalse();
        assertThat(result.exposures()).hasSize(1);
        assertThat(warnings).anyMatch(w -> w.code() == CollectionWarning.WarningCode.COLLECTION_FAILED);
    }

    @Test void missingIngressSpecCannotEstablishNoExposure() {
        service("svc-a", Map.of("instance", "a"));
        client.network().v1().ingresses().inNamespace("iam").resource(new IngressBuilder()
                .withNewMetadata().withName("malformed").withNamespace("iam").endMetadata().build()).create();
        assertGap("COLLECTION_FAILED");
    }

    @Test void missingRouteSpecCannotEstablishNoExposure() {
        service("svc-a", Map.of("instance", "a"));
        openshift().routes().inNamespace("iam").resource(new RouteBuilder()
                .withNewMetadata().withName("malformed").withNamespace("iam").endMetadata().build()).create();
        assertGap("COLLECTION_FAILED");
    }

    private void assertGap(String code) {
        var result = collector.collect(cluster, root, capabilities, warnings);
        assertThat(result.complete()).isFalse();
        assertThat(result.exposures()).isEmpty();
        assertThat(warnings).anyMatch(w -> w.code().name().equals(code));
    }

    private KeycloakWorkloadInfo deployment(String instance) {
        var deployment = client.apps().deployments().inNamespace("iam").resource(new DeploymentBuilder()
                .withNewMetadata().withNamespace("iam").withName("keycloak-" + instance).endMetadata()
                .withNewSpec().withReplicas(0).withNewTemplate().withNewMetadata()
                .addToLabels("instance", instance).addToLabels("app", "keycloak").endMetadata()
                .withNewSpec().addNewContainer().withName("keycloak").endContainer().endSpec().endTemplate().endSpec().build()).create();
        return new KeycloakWorkloadInfo(DeploymentMethod.DEPLOYMENT, "iam", deployment.getMetadata().getName(),
                0, 0, 0, 0, "apps/v1", "Deployment", deployment.getMetadata().getUid());
    }

    private void service(String name, Map<String, String> selector) {
        client.services().inNamespace("iam").resource(new ServiceBuilder().withNewMetadata().withName(name).withNamespace("iam")
                .endMetadata().withNewSpec().withSelector(selector).addNewPort().withName("http").withPort(80)
                .withTargetPort(new IntOrString(8080)).endPort().endSpec().build()).create();
    }

    private void ingress(String name, String host, String service, int port, boolean tls) {
        var builder = new IngressBuilder().withNewMetadata().withName(name).withNamespace("iam").endMetadata()
                .withNewSpec().addToRules(rule(host, service, port)).endSpec();
        if (tls) builder.editSpec().addNewTl().addToHosts(host).withSecretName("not-read").endTl().endSpec();
        client.network().v1().ingresses().inNamespace("iam").resource(builder.build()).create();
    }

    private IngressRule rule(String host, String service, int port) {
        return new IngressRuleBuilder().withHost(host).withNewHttp().addNewPath().withPath("/login").withPathType("Prefix")
                .withNewBackend().withNewService().withName(service).withNewPort().withNumber(port).endPort()
                .endService().endBackend().endPath().endHttp().build();
    }

    private OpenShiftClient openshift() {
        var oc = client.adapt(OpenShiftClient.class);
        capabilities = new ClusterApiCapabilities(ApiAvailability.SERVED, ApiAvailability.SERVED);
        return oc;
    }
}
