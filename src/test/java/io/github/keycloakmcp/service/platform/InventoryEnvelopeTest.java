package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.adapter.infrastructure.InfrastructureClientFactory;
import io.github.keycloakmcp.adapter.infrastructure.RedirectRejectingHttpClientFactory;
import io.github.keycloakmcp.discovery.*;
import io.github.keycloakmcp.domain.inventory.CollectionWarning.WarningCode;
import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
import io.github.keycloakmcp.target.*;

/** Raw HTTP fixtures deliberately bypass model builders and CRUD response normalization. */
class InventoryEnvelopeTest {
    private static final String ROOT = "/apis/apps/v1/namespaces/iam/deployments/sso";
    private static final String PODS = "/api/v1/namespaces/iam/pods?limit=501";
    private static final String NODES = "/api/v1/nodes?limit=501";
    private static final String DEPLOYMENT = """
        {"apiVersion":"apps/v1","kind":"Deployment","metadata":{"namespace":"iam","name":"sso","uid":"uid-sso"},
         "spec":{"replicas":2,"selector":{"matchLabels":{"app":"sso"}},"template":{"metadata":{"labels":{"app":"sso"}},
         "spec":{"containers":[{"name":"keycloak","image":"fixture"}]}}},
         "status":{"replicas":2,"readyReplicas":2,"availableReplicas":2}}
        """;
    private final Map<String, String> responses = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private KubernetesClient client;
    private ClusterClient cluster;
    private InfrastructureClientFactory factory;
    private EnvironmentDiscovery discovery;
    private InventoryService inventory;

    @BeforeEach
    void setUp() throws Exception {
        responses.put(ROOT, DEPLOYMENT);
        responses.put(NODES, list("v1", "Node", "[]"));
        responses.put(PODS, list("v1", "Pod", "[]"));
        responses.put("/api/v1/namespaces/iam/services?limit=501", list("v1", "Service", "[]"));
        for (String[] type : List.of(new String[]{"apps/v1", "ReplicaSet", "replicasets"},
                new String[]{"apps/v1", "Deployment", "deployments"},
                new String[]{"apps/v1", "StatefulSet", "statefulsets"},
                new String[]{"autoscaling/v2", "HorizontalPodAutoscaler", "horizontalpodautoscalers"},
                new String[]{"policy/v1", "PodDisruptionBudget", "poddisruptionbudgets"},
                new String[]{"networking.k8s.io/v1", "Ingress", "ingresses"})) {
            responses.put("/apis/" + type[0] + "/namespaces/iam/" + type[2] + "?limit=501", list(type[0], type[1], "[]"));
        }
        responses.put("/apis/apps/v1/namespaces/iam/deployments?limit=501", list("apps/v1", "Deployment", "[" + DEPLOYMENT + "]"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().toString();
            requests.add(exchange.getRequestMethod() + " " + path);
            byte[] body = responses.getOrDefault(path, "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responses.containsKey(path) ? 200 : 404, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        client = new KubernetesClientBuilder().withConfig(new ConfigBuilder(io.fabric8.kubernetes.client.Config.empty())
                .withMasterUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .withRequestTimeout(1000).withRequestRetryBackoffLimit(0).withAutoConfigure(false).build())
                .withHttpClientFactory(new RedirectRejectingHttpClientFactory()).build();
        cluster = mock(ClusterClient.class);
        when(cluster.kubernetes()).thenReturn(client);
        when(cluster.namespace()).thenReturn("iam");
        when(cluster.type()).thenReturn(InfrastructureType.KUBERNETES);
        factory = mock(InfrastructureClientFactory.class);
        when(factory.resolve(any())).thenReturn(Optional.of(cluster));
        discovery = mock(EnvironmentDiscovery.class);
        when(discovery.discover(any())).thenReturn(new EnvironmentInfo(RuntimeType.KUBERNETES,
                DetectionConfidence.CONFIRMED, "kubernetes", "iam", List.of(), "a", "v1.30.0", "kubernetes",
                InfrastructureType.KUBERNETES, new io.github.keycloakmcp.discovery.ClusterApiCapabilities(
                        io.github.keycloakmcp.discovery.ClusterApiCapabilities.ApiAvailability.NOT_SERVED,
                        io.github.keycloakmcp.discovery.ClusterApiCapabilities.ApiAvailability.NOT_SERVED)));
        TargetResolver resolver = mock(TargetResolver.class);
        when(resolver.require("a")).thenReturn(new Target(TargetId.of("a"), "a", TargetType.KEYCLOAK,
                TargetEnvironment.DEV, true, new KeycloakTargetConfiguration("http://fixture", "master", "client", "ref"),
                new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "c1", "iam", "infra",
                        new KubernetesInstallationBinding("apps/v1", "Deployment", "sso", "uid-sso")), null, Map.of()));
        inventory = new InventoryService(resolver, mock(TargetAuthorizationService.class), factory, discovery);
    }

    @AfterEach
    void cleanup() {
        Thread.interrupted();
        if (client != null) client.close();
        if (server != null) server.stop(0);
        assertThat(requests).allMatch(path -> path.startsWith("GET "));
    }

    @Test
    void completeEmptyListsRemainObservedZerosAndNodesAreReadOnlyOnce() {
        var result = inventory.collect("a");
        assertThat(result.warnings()).isEmpty();
        assertThat(result.cluster().nodeCount()).isZero();
        assertThat(result.hpa().present()).isFalse();
        assertThat(result.pdb().present()).isFalse();
        assertThat(inventory.toEvidence(result)).anyMatch(e -> "keycloak.pods.total".equals(e.key())
                && ((Number) e.value()).longValue() == 0);
        assertThat(requests.stream().filter(path -> path.equals("GET " + NODES))).hasSize(1);
        assertThat(requests).allMatch(path -> path.equals("GET " + ROOT) || path.endsWith("?limit=501"));
    }

    static Stream<String> incompletePods() {
        String item = "{\"apiVersion\":\"v1\",\"kind\":\"Pod\",\"metadata\":{\"name\":\"p\",\"namespace\":\"iam\",\"uid\":\"uid-p\"}}";
        String valid = list("v1", "Pod", "[" + item + "]");
        return Stream.of("{}", "{\"apiVersion\":\"v1\",\"kind\":\"PodList\",\"metadata\":{}}",
                list("v1", "Pod", "null"), valid.replace("PodList", "List"), valid.replace("\"v1\"", "\"v2\""),
                valid.replace("\"Pod\"", "\"Service\""), valid.replace("\"iam\"", "\"other\""),
                valid.replace(",\"uid\":\"uid-p\"", ""), list("v1", "Pod", "[" + item + "," + item + "]"),
                list("v1", "Pod", "[]").replace("\"metadata\":{}", "\"metadata\":{\"continue\":\"next\"}"),
                list("v1", "Pod", "[]").replace("\"metadata\":{}", "\"metadata\":{\"remainingItemCount\":1}"),
                "{\"apiVersion\":\"v1\",\"kind\":\"PodList\",\"metadata\":{},\"items\":[],\"items\":[]}",
                list("v1", "Pod", "[]") + "{}", " ".repeat(1_048_577));
    }

    @ParameterizedTest
    @MethodSource("incompletePods")
    void incompletePodsNeverBecomeHealthyZeros(String body) {
        responses.put(PODS, body);
        var result = inventory.collect("a");
        assertWarning(result, "pods");
        assertThat(inventory.toEvidence(result)).noneMatch(e -> List.of("pods", "topology").contains(e.category()));
        assertThat(inventory.toEvidence(result)).anyMatch(e -> e.key().equals("deployment.replicas") && Integer.valueOf(2).equals(e.value()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"apiVersion", "kind", "name", "uid", "namespace"})
    void omittedRootIdentityCannotBeFilledByModelDefaults(String field) throws Exception {
        var raw = new com.fasterxml.jackson.databind.ObjectMapper().readTree(DEPLOYMENT);
        var object = (com.fasterxml.jackson.databind.node.ObjectNode)
                (List.of("kind", "apiVersion").contains(field) ? raw : raw.get("metadata"));
        object.remove(field);
        responses.put(ROOT, raw.toString());
        var result = inventory.collect("a");
        assertWarning(result, "installation");
        assertThat(inventory.toEvidence(result)).noneMatch(e -> e.category().equals("workload"));
    }

    @Test
    void typedCollectionCanSupplyOmittedItemTypeWithoutInventingResourceIdentity() throws Exception {
        var raw = (com.fasterxml.jackson.databind.node.ObjectNode)
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(DEPLOYMENT);
        raw.remove(List.of("kind", "apiVersion"));
        responses.put("/apis/apps/v1/namespaces/iam/deployments?limit=501", list("apps/v1", "Deployment", "[" + raw + "]"));
        assertThat(inventory.collect("a").warnings()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"hpa", "pdb"})
    void missingPolicySpecCannotProvePolicyAbsence(String resource) {
        boolean hpa = "hpa".equals(resource);
        String api = hpa ? "autoscaling/v2" : "policy/v1";
        String kind = hpa ? "HorizontalPodAutoscaler" : "PodDisruptionBudget";
        String plural = hpa ? "horizontalpodautoscalers" : "poddisruptionbudgets";
        responses.put("/apis/" + api + "/namespaces/iam/" + plural + "?limit=501", list(api, kind,
                "[{\"apiVersion\":\"" + api + "\",\"kind\":\"" + kind + "\",\"metadata\":{\"name\":\"policy\",\"namespace\":\"iam\",\"uid\":\"uid-policy\"}}]"));
        var result = inventory.collect("a");
        assertWarning(result, resource);
        assertThat(inventory.toEvidence(result)).noneMatch(e -> e.category().equals(hpa ? "autoscaling" : "disruption"));
    }

    @Test
    void namespaceMismatchDoesNotReadTheCluster() {
        when(cluster.namespace()).thenReturn("other");
        assertWarning(inventory.collect("a"), "infrastructure");
        assertThat(requests).isEmpty();
        verifyNoInteractions(discovery);
    }

    @Test
    void failedDiscoveryCannotPromoteAConfiguredClusterHintToObservedDistribution() {
        when(discovery.discover(any())).thenReturn(new EnvironmentInfo(RuntimeType.UNKNOWN,
                DetectionConfidence.UNKNOWN, null, "iam", List.of(), "a", null, null));
        var result = inventory.collect("a");
        assertThat(result.cluster().distribution()).isNull();
        assertThat(inventory.toEvidence(result)).noneMatch(e -> e.key().equals("cluster.distribution"));
        assertThat(inventory.toEvidence(result)).anyMatch(e -> e.key().equals("deployment.replicas"));
    }

    @Test
    void contradictoryPodReadinessIsUnknownNotReady() {
        responses.put("/apis/apps/v1/namespaces/iam/replicasets?limit=501", list("apps/v1", "ReplicaSet", """
                [{"metadata":{"name":"rs","namespace":"iam","uid":"uid-rs","ownerReferences":[
                {"apiVersion":"apps/v1","kind":"Deployment","name":"sso","uid":"uid-sso","controller":true}]}}]
                """));
        responses.put(PODS, list("v1", "Pod", """
                [{"metadata":{"name":"pod","namespace":"iam","uid":"uid-pod","ownerReferences":[
                {"apiVersion":"apps/v1","kind":"ReplicaSet","name":"rs","uid":"uid-rs","controller":true}]},
                "status":{"conditions":[{"type":"Ready","status":"True"},{"type":"Ready","status":"False"}],
                "containerStatuses":[{"name":"keycloak","restartCount":0}]}}]
                """));
        var result = inventory.collect("a");
        assertWarning(result, "pods");
        assertThat(result.pods()).hasSize(1).allMatch(pod -> !pod.ready());
        assertThat(inventory.toEvidence(result)).noneMatch(e -> e.category().equals("pods"));
    }

    @Test
    void interruptionDoesNotCreateInfrastructureWorkAndPreservesFlag() {
        Thread.currentThread().interrupt();
        try {
            assertThat(inventory.collect("a").warnings()).singleElement().satisfies(warning -> {
                assertThat(warning.resource()).isEqualTo("collection-budget");
                assertThat(warning.code()).isEqualTo(WarningCode.OPERATION_INTERRUPTED);
            });
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verifyNoInteractions(factory, discovery);
            assertThat(requests).isEmpty();
        } finally { Thread.interrupted(); }
    }

    private static void assertWarning(InfrastructureInventory inventory, String resource) {
        assertThat(inventory.warnings()).anyMatch(warning -> resource.equals(warning.resource()));
    }

    private static String list(String api, String kind, String items) {
        return "{\"apiVersion\":\"" + api + "\",\"kind\":\"" + kind + "List\",\"metadata\":{},\"items\":" + items + "}";
    }
}
