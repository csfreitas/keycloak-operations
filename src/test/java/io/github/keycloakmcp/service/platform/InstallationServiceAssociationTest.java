package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.adapter.infrastructure.InfrastructureClientFactory;
import io.github.keycloakmcp.discovery.EnvironmentDiscovery;
import io.github.keycloakmcp.domain.inventory.DeploymentMethod;
import io.github.keycloakmcp.target.*;

@EnableKubernetesMockClient(crud = true)
class InstallationServiceAssociationTest {
    KubernetesMockServer server;
    KubernetesClient client;
    private final TargetResolver resolver = mock(TargetResolver.class);
    private final TargetAuthorizationService authorization = mock(TargetAuthorizationService.class);
    private final InfrastructureClientFactory factory = mock(InfrastructureClientFactory.class);
    private final EnvironmentDiscovery discovery = mock(EnvironmentDiscovery.class);
    private final ClusterClient cluster = mock(ClusterClient.class);
    private InventoryService inventory;
    private Deployment root;
    private Target target;

    @BeforeEach void setup() {
        io.github.keycloakmcp.testing.TypedKubernetesCrud.install(server);
        client.getConfiguration().setNamespace("iam");
        client.getConfiguration().setRequestRetryBackoffLimit(0);
        inventory = new InventoryService(resolver, authorization, factory, discovery);
        when(cluster.kubernetes()).thenReturn(client);
        when(cluster.namespace()).thenReturn("iam");
        root = deployment("a");
        target = target("a", root);
        when(resolver.require("a")).thenReturn(target);
    }

    @Test void returnsExclusiveServicesAndTheSameBoundedNamespaceSnapshot() {
        var other = deployment("b");
        var a = service("svc-a", "a");
        var b = service("svc-b", "b");
        var result = inventory.associatedServices(target, cluster);
        assertThat(result.complete()).isTrue();
        assertThat(result.namespace()).isEqualTo("iam");
        assertThat(result.services()).extracting(s -> s.getMetadata().getUid()).containsExactly(a.getMetadata().getUid());
        assertThat(result.namespaceServices()).extracting(s -> s.getMetadata().getUid())
                .containsExactlyInAnyOrder(a.getMetadata().getUid(), b.getMetadata().getUid());
        Target targetB = target("b", other);
        when(resolver.require("b")).thenReturn(targetB);
        assertThat(inventory.associatedServices(targetB, cluster).services()).extracting(s -> s.getMetadata().getName())
                .containsExactly("svc-b");
        verify(authorization).assertAllowed(target, TargetPermission.READ);
        verifyNoInteractions(factory, discovery);
    }

    @Test void focusedAssociationNeverRequestsNodesOrExposureApis() throws Exception {
        service("svc-a", "a");
        while (server.takeRequest(1, java.util.concurrent.TimeUnit.MILLISECONDS) != null) { }
        KubernetesClient observed = spy(client);
        when(cluster.kubernetes()).thenReturn(observed);
        assertThat(inventory.associatedServices(target, cluster).complete()).isTrue();
        verify(observed, never()).nodes();
        verify(observed, never()).network();
        verify(observed, never()).autoscaling();
        verify(observed, never()).policy();
        verify(observed, never()).secrets();
        verify(cluster, never()).openshift();
        verifyNoInteractions(factory, discovery);
        var paths = new ArrayList<String>();
        io.fabric8.mockwebserver.http.RecordedRequest request;
        while ((request = server.takeRequest(1, java.util.concurrent.TimeUnit.MILLISECONDS)) != null) {
            assertThat(request.getMethod()).isEqualTo("GET");
            paths.add(request.getPath());
        }
        assertThat(paths).isNotEmpty().allMatch(path -> path.matches(
                "(?:/apis/apps/v1/namespaces/iam/(?:deployments(?:/kc-a)?|statefulsets|replicasets)|/api/v1/namespaces/iam/(?:pods|services))(?:\\?limit=501)?"));
    }

    @Test void unrelatedSelectorlessAndExternalNameServicesDoNotEraseAnExclusiveAssociation() {
        Service own = service("svc-a", "a");
        Service selectorless = client.services().inNamespace("iam").resource(new ServiceBuilder()
                .withNewMetadata().withName("unrelated-selectorless").withNamespace("iam").endMetadata()
                .withNewSpec().endSpec().build()).create();
        Service external = client.services().inNamespace("iam").resource(new ServiceBuilder()
                .withNewMetadata().withName("unrelated-external").withNamespace("iam").endMetadata()
                .withNewSpec().withType("ExternalName").withExternalName("unrelated.example.test").endSpec().build()).create();
        var result = inventory.associatedServices(target, cluster);
        assertThat(result.complete()).isTrue();
        assertThat(result.services()).extracting(s -> s.getMetadata().getUid()).containsExactly(own.getMetadata().getUid());
        assertThat(result.namespaceServices()).extracting(s -> s.getMetadata().getUid()).containsExactlyInAnyOrder(
                own.getMetadata().getUid(), selectorless.getMetadata().getUid(), external.getMetadata().getUid());
        var warnings = new ArrayList<io.github.keycloakmcp.domain.inventory.CollectionWarning>();
        var workload = new io.github.keycloakmcp.domain.inventory.KeycloakWorkloadInfo(
                DeploymentMethod.DEPLOYMENT, "iam", root.getMetadata().getName(), 0, 0, 0, 0,
                "apps/v1", "Deployment", root.getMetadata().getUid());
        var selection = new InstallationServiceCollector().collect(cluster, workload, warnings);
        assertThat(selection.associationComplete()).isTrue();
        assertThat(warnings).anyMatch(w -> w.code() == io.github.keycloakmcp.domain.inventory.CollectionWarning.WarningCode.NOT_SUPPORTED);
    }

    @Test void unsupportedServiceWithRootSelectorStillMakesAssociationIncomplete() {
        service("svc-a", "a");
        client.services().inNamespace("iam").resource(new ServiceBuilder()
                .withNewMetadata().withName("related-external").withNamespace("iam").endMetadata()
                .withNewSpec().withType("ExternalName").withExternalName("unrelated.example.test")
                .addToSelector("instance", "a").endSpec().build()).create();
        assertThat(inventory.associatedServices(target, cluster).complete()).isFalse();
    }

    @Test void changedRegisteredTargetIsRejectedBeforeClusterUse() {
        Target changed = new Target(target.id(), "changed", target.type(), target.environment(), target.enabled(),
                target.keycloak(), target.infrastructure(), target.observability(), target.tags());
        when(resolver.require("a")).thenReturn(changed);
        assertThat(inventory.associatedServices(target, cluster).complete()).isFalse();
        verify(authorization).assertAllowed(changed, TargetPermission.READ);
        verify(cluster, never()).kubernetes();
    }

    @Test void missingBindingOrWrongClientNamespaceDoesNotReadResources() {
        when(cluster.namespace()).thenReturn("foreign");
        assertThat(inventory.associatedServices(target, cluster).complete()).isFalse();
        when(cluster.namespace()).thenReturn("iam");
        var unbound = new Target(target.id(), target.displayName(), target.type(), target.environment(), true,
                target.keycloak(), new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "cluster", "iam", "infra"),
                null, Map.of());
        when(resolver.require("a")).thenReturn(unbound);
        assertThat(inventory.associatedServices(unbound, cluster).complete()).isFalse();
        verify(cluster, never()).kubernetes();
    }

    @Test void authorizationFailurePrecedesClusterUse() {
        doThrow(new SecurityException("denied")).when(authorization).assertAllowed(target, TargetPermission.READ);
        assertThatThrownBy(() -> inventory.associatedServices(target, cluster)).isInstanceOf(SecurityException.class);
        verify(cluster, never()).kubernetes();
    }

    @Test void recreatedRootUidNeverAdoptsTheReplacement() {
        service("svc-a", "a");
        client.apps().deployments().inNamespace("iam").withName(root.getMetadata().getName()).delete();
        deployment("a");
        var result = inventory.associatedServices(target, cluster);
        assertThat(result.complete()).isFalse();
        assertThat(result.services()).isEmpty();
    }

    @Test void sharedWorkloadSelectorCannotSupplyAnExclusiveService() {
        deployment("b");
        client.services().inNamespace("iam").resource(new ServiceBuilder().withNewMetadata()
                .withName("shared").withNamespace("iam").endMetadata().withNewSpec().addToSelector("app", "keycloak")
                .endSpec().build()).create();
        assertIncomplete();
    }

    @Test void selectorMatchingForeignPodCannotSupplyAnExclusiveService() {
        service("svc-a", "a");
        client.pods().inNamespace("iam").resource(new PodBuilder().withNewMetadata().withName("foreign")
                .withNamespace("iam").addToLabels("instance", "a").endMetadata().build()).create();
        assertIncomplete();
    }

    @Test void deploymentReplicaSetOwnedPodIsAccepted() {
        service("svc-a", "a");
        var rs = client.apps().replicaSets().inNamespace("iam").resource(new ReplicaSetBuilder().withNewMetadata()
                .withName("a-rs").withNamespace("iam").addNewOwnerReference().withApiVersion("apps/v1").withKind("Deployment")
                .withName(root.getMetadata().getName()).withUid(root.getMetadata().getUid()).withController(true)
                .endOwnerReference().endMetadata().build()).create();
        ownedPod("pod-a", rs, "a");
        assertThat(inventory.associatedServices(target, cluster).complete()).isTrue();
    }

    @Test void statefulSetDirectOwnedPodIsAccepted() {
        var sts = client.apps().statefulSets().inNamespace("iam").resource(new StatefulSetBuilder().withNewMetadata()
                .withName("sts").withNamespace("iam").endMetadata().withNewSpec().withNewTemplate()
                .withNewMetadata().addToLabels("instance", "sts").endMetadata().withNewSpec().addNewContainer()
                .withName("keycloak").endContainer().endSpec().endTemplate().endSpec().build()).create();
        Target stsTarget = target("sts", sts);
        when(resolver.require("sts")).thenReturn(stsTarget);
        service("svc-sts", "sts");
        ownedPod("sts-0", sts, "sts");
        assertThat(inventory.associatedServices(stsTarget, cluster).services()).extracting(s -> s.getMetadata().getName())
                .containsExactly("svc-sts");
    }

    @Test void exactOperatorRootUsesItsOnlyControllerOwnedChild() {
        var cr = operator();
        ownDeployment(root, cr);
        service("svc-a", "a");
        Target operatorTarget = target("operator", cr);
        when(resolver.require("operator")).thenReturn(operatorTarget);
        var result = inventory.associatedServices(operatorTarget, cluster);
        assertThat(result.complete()).isTrue();
        assertThat(result.services()).extracting(s -> s.getMetadata().getName()).containsExactly("svc-a");
    }

    @Test void operatorWithoutOwnedChildOrWithMultipleChildrenRemainsUnknown() {
        var cr = operator();
        Target operatorTarget = target("operator", cr);
        when(resolver.require("operator")).thenReturn(operatorTarget);
        assertThat(inventory.associatedServices(operatorTarget, cluster).complete()).isFalse();
        ownDeployment(root, cr);
        ownDeployment(deployment("b"), cr);
        assertThat(inventory.associatedServices(operatorTarget, cluster).complete()).isFalse();
    }

    @Test void truncatedOperatorChildrenDoNotFallBackToFirstChild() {
        var cr = operator();
        Target operatorTarget = target("operator", cr);
        when(resolver.require("operator")).thenReturn(operatorTarget);
        server.expect().get().withPath("/apis/apps/v1/namespaces/iam/deployments?limit=501")
                .andReturn(200, new DeploymentListBuilder().withItems(root)
                        .withMetadata(new ListMetaBuilder().withRemainingItemCount(1L).build()).build()).always();
        assertThat(inventory.associatedServices(operatorTarget, cluster).complete()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"continue", "remaining", "negative-remaining", "duplicate-name", "duplicate-uid", "namespace", "missing-uid", "null-item", "too-many", "too-many-labels"})
    void incompleteServiceScopesAreNotEvidenceOfAbsence(String malformed) {
        Service a = service("svc-a", "a");
        var list = new ServiceListBuilder().withMetadata(new ListMetaBuilder().build()).withItems(a).build();
        switch (malformed) {
            case "continue" -> list.setMetadata(new ListMetaBuilder().withContinue("private-page-token").build());
            case "remaining" -> list.setMetadata(new ListMetaBuilder().withRemainingItemCount(1L).build());
            case "negative-remaining" -> list.setMetadata(new ListMetaBuilder().withRemainingItemCount(-1L).build());
            case "duplicate-name" -> list.setItems(List.of(a, new ServiceBuilder(a).editMetadata().withUid("different").endMetadata().build()));
            case "duplicate-uid" -> list.setItems(List.of(a, new ServiceBuilder(a).editMetadata().withName("different").endMetadata().build()));
            case "namespace" -> list.getItems().getFirst().getMetadata().setNamespace("foreign");
            case "missing-uid" -> list.getItems().getFirst().getMetadata().setUid(null);
            case "null-item" -> { var items = new ArrayList<Service>(); items.add(null); list.setItems(items); }
            case "too-many" -> list.setItems(java.util.Collections.nCopies(501, a));
            case "too-many-labels" -> {
                var labels = new java.util.LinkedHashMap<String, String>();
                for (int i = 0; i < 257; i++) labels.put("label-" + i, "value");
                list.getItems().getFirst().getMetadata().setLabels(labels);
            }
            default -> throw new AssertionError(malformed);
        }
        server.expect().get().withPath("/api/v1/namespaces/iam/services?limit=501").andReturn(200, list).always();
        assertIncomplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"deployments", "statefulsets", "pods", "replicasets", "services"})
    void missingRawItemsAtEveryAssociationPhaseRemainsUnknown(String resource) {
        service("svc-a", "a");
        String api = switch (resource) { case "pods", "services" -> "v1"; default -> "apps/v1"; };
        String kind = switch (resource) {
            case "deployments" -> "DeploymentList"; case "statefulsets" -> "StatefulSetList";
            case "pods" -> "PodList"; case "replicasets" -> "ReplicaSetList"; default -> "ServiceList";
        };
        String path = ("v1".equals(api) ? "/api/v1" : "/apis/apps/v1") + "/namespaces/iam/" + resource + "?limit=501";
        server.expect().get().withPath(path).andReturn(200,
                "{\"apiVersion\":\"" + api + "\",\"kind\":\"" + kind + "\",\"metadata\":{}}").always();
        assertIncomplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"kind", "apiVersion", "metadata"})
    void missingServiceEnvelopeIdentityDoesNotUseModelDefaults(String missing) throws Exception {
        service("svc-a", "a");
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var body = json.createObjectNode().put("apiVersion", "v1").put("kind", "ServiceList");
        body.putObject("metadata");
        body.putArray("items");
        body.remove(missing);
        server.expect().get().withPath("/api/v1/namespaces/iam/services?limit=501")
                .andReturn(200, json.writeValueAsString(body)).always();
        assertIncomplete();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 500})
    void deniedOrFailedServiceListDoesNotExportProviderDetails(int code) {
        server.expect().get().withPath("/api/v1/namespaces/iam/services?limit=501")
                .andReturn(code, new StatusBuilder().withCode(code).withMessage("private-server-canary").build()).always();
        var result = inventory.associatedServices(target, cluster);
        assertThat(result.complete()).isFalse();
        assertThat(result.toString()).doesNotContain("private-server-canary");
    }

    @Test void interruptedAssociationDoesNotReadResourcesOrClearFlag() {
        Thread.currentThread().interrupt();
        try {
            assertThat(inventory.associatedServices(target, cluster).complete()).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(cluster, never()).kubernetes();
        } finally { Thread.interrupted(); }
    }

    private void assertIncomplete() {
        var result = inventory.associatedServices(target, cluster);
        assertThat(result.complete()).isFalse();
        assertThat(result.services()).isEmpty();
    }

    private Deployment deployment(String instance) {
        return client.apps().deployments().inNamespace("iam").resource(new DeploymentBuilder().withNewMetadata()
                .withName("kc-" + instance).withNamespace("iam").endMetadata().withNewSpec().withNewTemplate()
                .withNewMetadata().addToLabels("instance", instance).addToLabels("app", "keycloak").endMetadata()
                .withNewSpec().addNewContainer().withName("keycloak").endContainer().endSpec().endTemplate().endSpec().build()).create();
    }

    private Service service(String name, String instance) {
        return client.services().inNamespace("iam").resource(new ServiceBuilder().withNewMetadata().withName(name)
                .withNamespace("iam").addToLabels("installation", instance).endMetadata().withNewSpec()
                .addToSelector("instance", instance).addNewPort().withName("metrics").withPort(9000).endPort().endSpec().build()).create();
    }

    private static Target target(String id, HasMetadata resource) {
        return new Target(TargetId.of(id), id, TargetType.KEYCLOAK, TargetEnvironment.DEV, true,
                new KeycloakTargetConfiguration("http://localhost", "master", "client", "keycloak-ref"),
                new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "approved-cluster", "iam", "infra-ref",
                        new KubernetesInstallationBinding(resource.getApiVersion(), resource.getKind(), resource.getMetadata().getName(),
                                resource.getMetadata().getUid())), null, Map.of());
    }

    private void ownedPod(String name, HasMetadata owner, String instance) {
        client.pods().inNamespace("iam").resource(new PodBuilder().withNewMetadata().withName(name).withNamespace("iam")
                .addToLabels("instance", instance).addNewOwnerReference().withApiVersion(owner.getApiVersion()).withKind(owner.getKind())
                .withName(owner.getMetadata().getName()).withUid(owner.getMetadata().getUid()).withController(true)
                .endOwnerReference().endMetadata().build()).create();
    }

    private GenericKubernetesResource operator() {
        var definition = new ResourceDefinitionContext.Builder().withGroup("k8s.keycloak.org").withVersion("v2alpha1")
                .withPlural("keycloaks").withNamespaced(true).build();
        var cr = new GenericKubernetesResource();
        cr.setApiVersion("k8s.keycloak.org/v2alpha1"); cr.setKind("Keycloak");
        cr.setMetadata(new ObjectMetaBuilder().withName("operator").withNamespace("iam").build());
        cr.setAdditionalProperty("spec", Map.of("instances", 1));
        return client.genericKubernetesResources(definition).inNamespace("iam").resource(cr).create();
    }

    private void ownDeployment(Deployment workload, GenericKubernetesResource owner) {
        client.apps().deployments().inNamespace("iam").withName(workload.getMetadata().getName()).edit(d -> new DeploymentBuilder(d)
                .editMetadata().addNewOwnerReference().withApiVersion(owner.getApiVersion()).withKind(owner.getKind())
                .withName(owner.getMetadata().getName()).withUid(owner.getMetadata().getUid()).withController(true)
                .endOwnerReference().endMetadata().build());
    }
}
