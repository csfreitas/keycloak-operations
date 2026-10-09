package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.*;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.kubernetes.client.server.mock.*;
import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.target.KubernetesInstallationBinding;

@EnableKubernetesMockClient(crud = true)
class InstallationCandidateCollectorTest {
    KubernetesMockServer server;
    KubernetesClient client;
    ClusterClient cluster;
    private static final ObjectMapper JSON = new ObjectMapper();
    private String apiGroups;
    InstallationCandidateCollector collector = new InstallationCandidateCollector();
    @BeforeEach void setup() {
        io.github.keycloakmcp.testing.TypedKubernetesCrud.install(server);
        client.getConfiguration().setNamespace("approved");
        cluster = mock(ClusterClient.class);
        when(cluster.kubernetes()).thenReturn(client);
        when(cluster.namespace()).thenReturn("approved");
        apiGroups = "{\"apiVersion\":\"v1\",\"kind\":\"APIGroupList\",\"groups\":[]}";
        server.expect().get().withPath("/apis").andReply(200, request -> apiGroups).always();
    }

    @Test void namespaceIsExplicitAndCandidatesAreMetadataOnly() throws Exception {
        for (String namespace : new String[]{"approved", "foreign"}) {
            client.apps().deployments().inNamespace(namespace).resource(new DeploymentBuilder().withNewMetadata()
                    .withName("same-name").withNamespace(namespace).addToAnnotations("secret", "fixture-private-value")
                    .endMetadata().build()).create();
        }
        var found = collector.discover(cluster, "approved");
        assertThat(found).hasSize(1);
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(found)).doesNotContain("fixture-private-value", "annotations");
        assertThat(collector.stillExists(cluster, "approved", found.getFirst())).isTrue();
        assertThat(collector.stillExists(cluster, "foreign", found.getFirst())).isFalse();
    }

    @Test void deniedListDoesNotBecomeAnEmptySuccessfulDiscovery() {
        server.expect().get().withPath("/apis/apps/v1/namespaces/approved/deployments?limit=101")
                .andReturn(403, new StatusBuilder().withCode(403).withMessage("sensitive-error-fixture").build()).always();
        assertThatThrownBy(() -> collector.discover(cluster, "approved")).isInstanceOf(McpException.class)
                .hasMessageNotContaining("sensitive-error-fixture");
    }

    @Test void partialPageCannotBeConfirmed() {
        server.expect().get().withPath("/apis/apps/v1/namespaces/approved/deployments?limit=101")
                .andReturn(200, new DeploymentListBuilder().withMetadata(new ListMetaBuilder().withContinue("next").build()).build()).always();
        assertThatThrownBy(() -> collector.discover(cluster, "approved")).isInstanceOf(McpException.class);
    }

    @Test void selectedUidMustStillExist() {
        client.apps().statefulSets().inNamespace("approved").resource(new StatefulSetBuilder().withNewMetadata()
                .withName("rhbk").withNamespace("approved").endMetadata().build()).create();
        var binding = collector.discover(cluster, "approved").getFirst();
        client.apps().statefulSets().inNamespace("approved").withName("rhbk").delete();
        client.apps().statefulSets().inNamespace("approved").resource(new StatefulSetBuilder().withNewMetadata()
                .withName("rhbk").withNamespace("approved").endMetadata().build()).create();
        assertThat(collector.stillExists(cluster, "approved", binding)).isFalse();
    }

    @Test void operatorApiUsesAdvertisedPreferredVersion() {
        apiGroups = """
                {"apiVersion":"v1","kind":"APIGroupList","groups":[{"name":"k8s.keycloak.org",
                 "versions":[{"groupVersion":"k8s.keycloak.org/v2beta1","version":"v2beta1"}],
                 "preferredVersion":{"groupVersion":"k8s.keycloak.org/v2beta1","version":"v2beta1"}}]}
                """;
        var definition = new ResourceDefinitionContext.Builder().withGroup("k8s.keycloak.org").withVersion("v2beta1")
                .withPlural("keycloaks").withNamespaced(true).build();
        var resource = new GenericKubernetesResource();
        resource.setApiVersion("k8s.keycloak.org/v2beta1"); resource.setKind("Keycloak");
        resource.setMetadata(new ObjectMetaBuilder().withName("rhbk").withNamespace("approved").build());
        client.genericKubernetesResources(definition).inNamespace("approved").resource(resource).create();
        assertThat(collector.discover(cluster, "approved")).singleElement().satisfies(b ->
                assertThat(b.apiVersion()).isEqualTo("k8s.keycloak.org/v2beta1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-items", "null-items", "object-items", "missing-kind", "wrong-kind", "generic-list",
            "missing-version", "wrong-version", "missing-metadata", "item-kind", "item-version", "namespace", "missing-uid",
            "invalid-uid", "duplicate-name", "duplicate-uid", "null-item", "continue", "remaining", "negative-remaining", "too-many"})
    void malformedRawCandidateListCannotBecomeACompleteDiscovery(String malformed) throws Exception {
        ObjectNode body = list("apps/v1", "DeploymentList", "Deployment", 1, "deploy");
        ObjectNode item = (ObjectNode) body.withArray("items").get(0);
        switch (malformed) {
            case "missing-items" -> body.remove("items");
            case "null-items" -> body.putNull("items");
            case "object-items" -> body.set("items", JSON.createObjectNode());
            case "missing-kind" -> body.remove("kind");
            case "wrong-kind" -> body.put("kind", "StatefulSetList");
            case "generic-list" -> { body.put("apiVersion", "v1"); body.put("kind", "List"); }
            case "missing-version" -> body.remove("apiVersion");
            case "wrong-version" -> body.put("apiVersion", "apps/v1beta1");
            case "missing-metadata" -> body.remove("metadata");
            case "item-kind" -> item.putNull("kind");
            case "item-version" -> item.putNull("apiVersion");
            case "namespace" -> ((ObjectNode) item.get("metadata")).put("namespace", "foreign");
            case "missing-uid" -> ((ObjectNode) item.get("metadata")).remove("uid");
            case "invalid-uid" -> ((ObjectNode) item.get("metadata")).put("uid", "private/invalid-uid");
            case "duplicate-name" -> {
                ObjectNode duplicate = item.deepCopy(); ((ObjectNode) duplicate.get("metadata")).put("uid", "other-uid");
                body.withArray("items").add(duplicate);
            }
            case "duplicate-uid" -> {
                ObjectNode duplicate = item.deepCopy(); ((ObjectNode) duplicate.get("metadata")).put("name", "other-name");
                body.withArray("items").add(duplicate);
            }
            case "null-item" -> body.withArray("items").addNull();
            case "continue" -> ((ObjectNode) body.get("metadata")).put("continue", "private-page-token");
            case "remaining" -> ((ObjectNode) body.get("metadata")).put("remainingItemCount", 1);
            case "negative-remaining" -> ((ObjectNode) body.get("metadata")).put("remainingItemCount", -1);
            case "too-many" -> body = list("apps/v1", "DeploymentList", "Deployment", 101, "deploy");
            default -> throw new AssertionError(malformed);
        }
        server.expect().get().withPath("/apis/apps/v1/namespaces/approved/deployments?limit=101")
                .andReturn(200, JSON.writeValueAsString(body)).always();
        assertThatThrownBy(() -> collector.discover(cluster, "approved")).isInstanceOf(McpException.class)
                .hasMessageNotContaining("private-page-token").hasMessageNotContaining("invalid-uid").hasMessageNotContaining("foreign");
    }

    @Test void typedListItemsMayInheritOmittedTypeOnlyFromValidatedEnvelope() throws Exception {
        ObjectNode body = list("apps/v1", "DeploymentList", "Deployment", 1, "deploy");
        ObjectNode item = (ObjectNode) body.withArray("items").get(0);
        item.remove("apiVersion");
        item.remove("kind");
        server.expect().get().withPath("/apis/apps/v1/namespaces/approved/deployments?limit=101")
                .andReturn(200, JSON.writeValueAsString(body)).always();
        assertThat(collector.discover(cluster, "approved")).containsExactly(
                new KubernetesInstallationBinding("apps/v1", "Deployment", "deploy-0", "deploy-uid-0"));
    }

    @Test void combinedCandidateLimitDoesNotReturnAFavorableSubset() throws Exception {
        server.expect().get().withPath("/apis/apps/v1/namespaces/approved/deployments?limit=101")
                .andReturn(200, JSON.writeValueAsString(list("apps/v1", "DeploymentList", "Deployment", 51, "deploy"))).always();
        server.expect().get().withPath("/apis/apps/v1/namespaces/approved/statefulsets?limit=101")
                .andReturn(200, JSON.writeValueAsString(list("apps/v1", "StatefulSetList", "StatefulSet", 50, "sts"))).always();
        assertThatThrownBy(() -> collector.discover(cluster, "approved")).isInstanceOf(McpException.class);
    }

    @Test void duplicateUidAcrossKindsDoesNotBecomeTwoConfirmableCandidates() throws Exception {
        server.expect().get().withPath("/apis/apps/v1/namespaces/approved/deployments?limit=101")
                .andReturn(200, JSON.writeValueAsString(list("apps/v1", "DeploymentList", "Deployment", 1, "same"))).always();
        server.expect().get().withPath("/apis/apps/v1/namespaces/approved/statefulsets?limit=101")
                .andReturn(200, JSON.writeValueAsString(list("apps/v1", "StatefulSetList", "StatefulSet", 1, "same"))).always();
        assertThatThrownBy(() -> collector.discover(cluster, "approved")).isInstanceOf(McpException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"kind", "apiVersion", "metadata", "namespace", "uid"})
    void malformedGetCannotReconfirmPreviouslyReviewedIdentity(String missing) throws Exception {
        ObjectNode item = (ObjectNode) list("apps/v1", "DeploymentList", "Deployment", 1, "deploy").get("items").get(0);
        if ("namespace".equals(missing) || "uid".equals(missing)) ((ObjectNode) item.get("metadata")).remove(missing);
        else item.remove(missing);
        server.expect().get().withPath("/apis/apps/v1/namespaces/approved/deployments/deploy-0")
                .andReturn(200, JSON.writeValueAsString(item)).always();
        assertThat(collector.stillExists(cluster, "approved", new KubernetesInstallationBinding("apps/v1", "Deployment", "deploy-0", "deploy-uid-0"))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"apiVersion\":\"v1\",\"kind\":\"APIGroupList\"}",
            "{\"apiVersion\":\"v1\",\"kind\":\"APIGroupList\",\"groups\":null}",
            "{\"apiVersion\":\"v1\",\"kind\":\"APIGroupList\",\"groups\":[null]}"})
    void malformedApiDiscoveryDoesNotSilentlyOmitOperatorCandidates(String payload) {
        apiGroups = payload;
        assertThatThrownBy(() -> collector.discover(cluster, "approved")).isInstanceOf(McpException.class);
    }

    @Test void validEmptyEnvelopeProvesAnEmptyCandidateScope() {
        assertThat(collector.discover(cluster, "approved")).isEmpty();
    }

    @Test void wrongScopeIsDeniedBeforeReadingCluster() {
        assertThatThrownBy(() -> collector.discover(cluster, "foreign")).isInstanceOf(McpException.class);
        verify(cluster, never()).kubernetes();
    }

    @Test void interruptedDiscoveryPerformsNoReadAndPreservesFlag() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> collector.discover(cluster, "approved"))
                    .isInstanceOfSatisfying(McpException.class, error -> assertThat(error.getCode()).isEqualTo(ErrorCode.EVIDENCE_COLLECTION_FAILED))
                    .hasMessage("Candidate discovery unavailable or incomplete; check scope, API permissions and the 100-candidate limit")
                    .hasNoCause();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(cluster, never()).kubernetes();
        } finally { Thread.interrupted(); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void scopedAbortPropagatesWithoutReadingClusterAndRestoresCallerScope(boolean interrupted) {
        var clock = new AtomicLong();
        var budget = new CollectionBudget(Duration.ofSeconds(1), clock::get);
        try (var scope = CollectionBudget.open("a", budget)) {
            if (interrupted) Thread.currentThread().interrupt();
            else clock.set(1_000_000_000);
            assertThatThrownBy(() -> collector.discover(cluster, "approved"))
                    .isInstanceOf(CollectionBudget.Aborted.class)
                    .hasMessage(interrupted ? "OPERATION_INTERRUPTED" : "OPERATION_BUDGET_EXCEEDED")
                    .hasNoCause();
            assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupted);
            assertThat(CollectionBudget.current()).isSameAs(scope.budget());
            verify(cluster, never()).kubernetes();
        } finally { Thread.interrupted(); }
        assertThat(CollectionBudget.current()).isNull();
    }

    private static ObjectNode list(String api, String kind, String itemKind, int count, String prefix) {
        ObjectNode result = JSON.createObjectNode().put("apiVersion", api).put("kind", kind);
        result.putObject("metadata");
        var items = result.putArray("items");
        for (int i = 0; i < count; i++) {
            ObjectNode item = items.addObject().put("apiVersion", api).put("kind", itemKind);
            item.putObject("metadata").put("namespace", "approved").put("name", prefix + "-" + i).put("uid", prefix + "-uid-" + i);
        }
        return result;
    }
}
