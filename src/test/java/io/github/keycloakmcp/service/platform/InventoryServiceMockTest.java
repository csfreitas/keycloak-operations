package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.adapter.infrastructure.InfrastructureClientFactory;
import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.discovery.DetectionConfidence;
import io.github.keycloakmcp.discovery.EnvironmentDiscovery;
import io.github.keycloakmcp.discovery.EnvironmentInfo;
import io.github.keycloakmcp.discovery.RuntimeType;
import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
import io.github.keycloakmcp.target.InfrastructureTargetConfiguration;
import io.github.keycloakmcp.target.InfrastructureType;
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

    @BeforeEach
    void setUp() {
        targetResolver = mock(TargetResolver.class);
        authz = mock(TargetAuthorizationService.class);
        clientFactory = mock(InfrastructureClientFactory.class);
        environmentDiscovery = mock(EnvironmentDiscovery.class);

        inventoryService = new InventoryService(targetResolver, authz, clientFactory, environmentDiscovery);

        ClusterClient clusterClient = mock(ClusterClient.class);
        when(clusterClient.kubernetes()).thenReturn(client);
        when(clusterClient.namespace()).thenReturn("rhbk");
        when(clusterClient.type()).thenReturn(InfrastructureType.KUBERNETES);
        when(clusterClient.openshift()).thenReturn(Optional.empty());
        when(clientFactory.resolve(any())).thenReturn(Optional.of(clusterClient));

        when(environmentDiscovery.discover(any())).thenReturn(new EnvironmentInfo(
                RuntimeType.KUBERNETES,
                DetectionConfidence.CONFIRMED,
                "kubernetes",
                "rhbk",
                List.of("mock"),
                "target-a",
                "v1.29.0",
                "kubernetes"));
    }

    @Test
    void collectsDeploymentReplicasAndEmitsEvidenceWithTargetId() {
        Target target = target("target-a", "rhbk");
        when(targetResolver.require("target-a")).thenReturn(target);

        client.apps().deployments().inNamespace("rhbk").resource(new DeploymentBuilder()
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

        client.pods().inNamespace("rhbk").resource(new PodBuilder()
                .withNewMetadata().withName("keycloak-0").withNamespace("rhbk")
                .addToLabels("app", "keycloak").endMetadata()
                .withNewSpec().withNodeName("node-a").endSpec()
                .withNewStatus().addNewCondition().withType("Ready").withStatus("True").endCondition().endStatus()
                .build()).create();

        InfrastructureInventory inventory = inventoryService.collect("target-a");
        assertThat(inventory.targetId()).isEqualTo("target-a");
        assertThat(inventory.keycloak().desiredReplicas()).isEqualTo(3);
        assertThat(inventory.pods()).isNotEmpty();

        List<Evidence> evidence = inventoryService.toEvidence(inventory);
        assertThat(evidence).isNotEmpty();
        assertThat(evidence).allMatch(e -> "target-a".equals(e.targetId()));
        assertThat(evidence).anyMatch(e -> "deployment.replicas".equals(e.key()) && Integer.valueOf(3).equals(e.value()));
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

    private static Target target(String id, String namespace) {
        return new Target(
                TargetId.of(id),
                id,
                TargetType.KEYCLOAK,
                TargetEnvironment.PRD,
                true,
                new KeycloakTargetConfiguration("http://kc", "master", "c", "lab-a"),
                new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "c1", namespace, "infra-a"),
                null,
                Map.of());
    }
}
