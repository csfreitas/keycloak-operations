package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.github.keycloakmcp.adapter.infrastructure.*;
import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.discovery.*;
import io.github.keycloakmcp.domain.inventory.CollectionWarning;
import io.github.keycloakmcp.target.*;

class InventoryCollectionBudgetTest {
    private static final String NODES = "/api/v1/nodes?limit=501";
    private static final String ROOT = "/apis/apps/v1/namespaces/iam/deployments/sso";
    private static final String HPA = "/apis/autoscaling/v2/namespaces/iam/horizontalpodautoscalers?limit=501";
    private static final String PDB = "/apis/policy/v1/namespaces/iam/poddisruptionbudgets?limit=501";
    private static final String DEPLOYMENT = """
            {"apiVersion":"apps/v1","kind":"Deployment","metadata":{"name":"sso","namespace":"iam","uid":"uid-sso"},
             "spec":{"replicas":2,"selector":{"matchLabels":{"app":"sso"}},"template":{"metadata":{"labels":{"app":"sso"}},"spec":{"containers":[{"name":"keycloak"}]}}},
             "status":{"replicas":2,"readyReplicas":2,"availableReplicas":2}}
            """;
    private final AtomicLong clock = new AtomicLong();
    private final Map<String, String> replies = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final InfrastructureClientFactory factory = mock(InfrastructureClientFactory.class);
    private final TargetResolver resolver = mock(TargetResolver.class);
    private final TargetAuthorizationService authorization = mock(TargetAuthorizationService.class);
    private HttpServer server;
    private KubernetesClient client;
    private ClusterClient cluster;
    private InventoryService inventory;
    private Target target;
    private volatile String expireAt;

    @BeforeEach void setup() throws Exception {
        replies.put(ROOT, DEPLOYMENT);
        replies.put(NODES, list("v1", "Node", "[]"));
        replies.put("/api/v1/namespaces/iam/pods?limit=501", list("v1", "Pod", "[]"));
        replies.put("/api/v1/namespaces/iam/services?limit=501", list("v1", "Service", "[]"));
        for (String[] type : List.of(new String[]{"apps/v1", "Deployment", "deployments"},
                new String[]{"apps/v1", "StatefulSet", "statefulsets"}, new String[]{"apps/v1", "ReplicaSet", "replicasets"},
                new String[]{"autoscaling/v2", "HorizontalPodAutoscaler", "horizontalpodautoscalers"},
                new String[]{"policy/v1", "PodDisruptionBudget", "poddisruptionbudgets"},
                new String[]{"networking.k8s.io/v1", "Ingress", "ingresses"})) {
            replies.put("/apis/" + type[0] + "/namespaces/iam/" + type[2] + "?limit=501", list(type[0], type[1], "[]"));
        }
        replies.put("/apis/apps/v1/namespaces/iam/deployments?limit=501", list("apps/v1", "Deployment", "[" + DEPLOYMENT + "]"));
        replies.put("/apis", "{\"apiVersion\":\"v1\",\"kind\":\"APIGroupList\",\"groups\":[]}");
        replies.put("/version", "{\"gitVersion\":\"v1.30.0\"}");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().toString();
            requests.add(path);
            if (path.equals(expireAt)) clock.set(1_000_000_000);
            byte[] body = replies.getOrDefault(path, "{}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(replies.containsKey(path) ? 200 : 404, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        Config config = Config.empty();
        config.setMasterUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config.setOauthToken("budget-fixture"); config.setAutoConfigure(false);
        config.setRequestTimeout(1000); config.setRequestRetryBackoffLimit(0);
        client = new KubernetesClientBuilder().withConfig(config).withHttpClientFactory(new RedirectRejectingHttpClientFactory()).build();
        cluster = mock(ClusterClient.class);
        when(cluster.namespace()).thenReturn("iam"); when(cluster.kubernetes()).thenReturn(client);
        when(cluster.type()).thenReturn(InfrastructureType.KUBERNETES);
        when(factory.resolve(any())).thenReturn(Optional.of(cluster));
        var discovery = mock(EnvironmentDiscovery.class);
        when(discovery.discover(any())).thenReturn(new EnvironmentInfo(RuntimeType.KUBERNETES, DetectionConfidence.CONFIRMED,
                "kubernetes", "iam", List.of(), "a", "v1.30.0", "kubernetes",
                InfrastructureType.KUBERNETES, new io.github.keycloakmcp.discovery.ClusterApiCapabilities(
                        io.github.keycloakmcp.discovery.ClusterApiCapabilities.ApiAvailability.NOT_SERVED,
                        io.github.keycloakmcp.discovery.ClusterApiCapabilities.ApiAvailability.NOT_SERVED)));
        target = target("a");
        when(resolver.require("a")).thenReturn(target);
        when(resolver.require("b")).thenReturn(target("b"));
        inventory = new InventoryService(resolver, authorization, factory, discovery);
    }

    @AfterEach void cleanup() {
        if (client != null) client.close();
        if (server != null) server.stop(0);
        assertThat(CollectionBudget.current()).isNull();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test void expiryAfterHpaPreservesEarlierObservationsAndSkipsLaterNetworkWork() {
        expireAt = PDB;
        try (var scope = CollectionBudget.open("a", new CollectionBudget(Duration.ofSeconds(1), clock::get))) {
            var result = inventory.collect("a");
            assertThat(result.warnings()).anyMatch(w -> w.resource().equals("collection-budget") && w.code() == CollectionWarning.WarningCode.OPERATION_BUDGET_EXCEEDED);
            assertThat(result.keycloak().desiredReplicas()).isEqualTo(2);
            assertThat(result.cluster().nodeCount()).isZero();
            assertThat(result.hpa().present()).isFalse();
            var evidence = inventory.toEvidence(result);
            assertThat(evidence).anyMatch(e -> e.key().equals("deployment.replicas") && e.value().equals(2));
            assertThat(evidence).anyMatch(e -> e.key().equals("keycloak.hpa.present") && e.value().equals(false));
            assertThat(evidence).noneMatch(e -> e.category().equals("disruption") || e.category().equals("networking"));
            assertThat(evidence).anyMatch(e -> e.key().equals("infrastructure.collection.complete") && e.value().equals(false));
            assertThat(requests).contains(HPA, PDB);
            assertThat(requests.getLast()).isEqualTo(PDB);
            assertThat(requests).noneMatch(path -> path.contains("services") || path.contains("ingresses"));
            assertThat(CollectionBudget.current()).isSameAs(scope.budget());
        }
    }

    @Test void expiredParentNeverStartsClientOrDiscoveryAndDoesNotInventZeroFacts() {
        var budget = new CollectionBudget(Duration.ofSeconds(1), clock::get);
        clock.set(1_000_000_000);
        try (var scope = CollectionBudget.open("a", budget)) {
            var result = inventory.collect("a");
            verify(authorization).assertAllowed(target, TargetPermission.READ);
            verifyNoInteractions(factory);
            assertThat(requests).isEmpty();
            assertThat(result.warnings()).anyMatch(w -> w.code() == CollectionWarning.WarningCode.OPERATION_BUDGET_EXCEEDED);
            assertThat(result.pods()).isNull();
            assertThat(result.topology()).isNull();
            assertThat(inventory.toEvidence(result)).noneMatch(e -> List.of("workload", "pods", "topology").contains(e.category()));
        }
    }

    @Test void interruptPreservesFlagAndProducesExplicitIncompleteMarker() {
        Thread.currentThread().interrupt();
        try {
            var result = inventory.collect("a");
            assertThat(result.warnings()).anyMatch(w -> w.code() == CollectionWarning.WarningCode.OPERATION_INTERRUPTED);
            assertThat(result.pods()).isNull();
            assertThat(result.topology()).isNull();
            assertThat(inventory.toEvidence(result)).noneMatch(e -> List.of("workload", "pods", "topology").contains(e.category()));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verifyNoInteractions(factory);
            assertThat(CollectionBudget.current()).isNull();
        } finally { Thread.interrupted(); }
    }

    @Test void scopeCannotSwitchTargetEvenWhenBothTargetsAreAuthorized() {
        try (var scope = CollectionBudget.open("a", 1000)) {
            assertThatThrownBy(() -> inventory.collect("b")).isInstanceOf(IllegalStateException.class).hasMessage("Collection scope target mismatch");
            assertThat(CollectionBudget.current()).isSameAs(scope.budget());
            verifyNoInteractions(factory);
        }
    }

    @Test void independentInvocationGetsItsOwnBudgetAndClosesIt() {
        when(factory.resolve(any())).thenAnswer(invocation -> {
            assertThat(CollectionBudget.current()).isNotNull();
            return Optional.of(cluster);
        });
        assertThat(inventory.collect("a").warnings()).isEmpty();
        assertThat(CollectionBudget.current()).isNull();
        assertThat(inventory.collect("b").targetId()).isEqualTo("b");
        assertThat(CollectionBudget.current()).isNull();
    }

    @Test void nestedDiscoveryBudgetCannotRenewParentAndKeepsCompletedRuntimeObservation() {
        var discovery = new EnvironmentDiscovery(mock(io.github.keycloakmcp.config.DiscoveryConfig.class), factory);
        expireAt = "/version";
        try (var scope = CollectionBudget.open("a", new CollectionBudget(Duration.ofSeconds(1), clock::get))) {
            var result = discovery.discover(target);
            assertThat(result.runtime()).isEqualTo(RuntimeType.KUBERNETES);
            assertThat(result.clusterVersion()).isNull();
            assertThat(result.evidence()).contains("OPERATION_BUDGET_EXCEEDED");
            assertThat(requests).containsExactly("/apis", "/version");
            assertThat(CollectionBudget.current()).isSameAs(scope.budget());
        }
    }

    @Test void expiredAssociationDoesNotReadTheClusterOrOverwriteParentScope() {
        var budget = new CollectionBudget(Duration.ofSeconds(1), clock::get);
        clock.set(1_000_000_000);
        try (var scope = CollectionBudget.open("a", budget)) {
            assertThat(inventory.associatedServices(target, cluster).complete()).isFalse();
            assertThat(requests).isEmpty();
            assertThat(CollectionBudget.current()).isSameAs(scope.budget());
        }
    }

    private Target target(String id) {
        return new Target(TargetId.of(id), id, TargetType.KEYCLOAK, TargetEnvironment.DEV, true,
                new KeycloakTargetConfiguration("http://fixture", "master", "client", "ref"),
                new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "cluster", "iam", "infra",
                        new KubernetesInstallationBinding("apps/v1", "Deployment", "sso", "uid-sso")), null, Map.of());
    }
    private static String list(String apiVersion, String kind, String items) {
        return "{\"apiVersion\":\"" + apiVersion + "\",\"kind\":\"" + kind + "List\",\"metadata\":{},\"items\":" + items + "}";
    }
}
