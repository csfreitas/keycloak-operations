package io.github.keycloakmcp.testing;

import java.lang.reflect.Field;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.server.mock.KubernetesCrudDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMixedDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.http.MockResponse;
import io.fabric8.mockwebserver.http.RecordedRequest;

/**
 * Fabric8's CRUD fixture emits generic v1/List envelopes for every typed endpoint.
 * Real Kubernetes resource lists have their own API version and kind. Correct only
 * the CRUD fallback, never explicit expectations (including malformed test bodies).
 * Reflection is confined to this pinned test dependency; fail if its shape changes.
 */
public final class TypedKubernetesCrud {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, String> KINDS = Map.ofEntries(
            Map.entry("v1/pods", "PodList"), Map.entry("v1/services", "ServiceList"), Map.entry("v1/nodes", "NodeList"),
            Map.entry("apps/v1/deployments", "DeploymentList"), Map.entry("apps/v1/statefulsets", "StatefulSetList"),
            Map.entry("apps/v1/replicasets", "ReplicaSetList"), Map.entry("autoscaling/v2/horizontalpodautoscalers", "HorizontalPodAutoscalerList"),
            Map.entry("policy/v1/poddisruptionbudgets", "PodDisruptionBudgetList"), Map.entry("networking.k8s.io/v1/ingresses", "IngressList"),
            Map.entry("route.openshift.io/v1/routes", "RouteList"));

    private TypedKubernetesCrud() { }

    public static void install(KubernetesMockServer server) {
        try {
            Field dispatcherField = KubernetesMockServer.class.getDeclaredField("dispatcher");
            dispatcherField.setAccessible(true);
            Object dispatcher = dispatcherField.get(server);
            if (!(dispatcher instanceof KubernetesMixedDispatcher)) throw new IllegalStateException("CRUD fixture required");
            Field fallbackField = KubernetesMixedDispatcher.class.getDeclaredField("kubernetesCrudDispatcher");
            fallbackField.setAccessible(true);
            KubernetesCrudDispatcher delegate = (KubernetesCrudDispatcher) fallbackField.get(dispatcher);
            if (!(delegate instanceof TypedFallback)) fallbackField.set(dispatcher, new TypedFallback(delegate));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to adapt the pinned CRUD test fixture", e);
        }
    }

    private static final class TypedFallback extends KubernetesCrudDispatcher {
        private final KubernetesCrudDispatcher delegate;

        private TypedFallback(KubernetesCrudDispatcher delegate) { this.delegate = delegate; }

        @Override public MockResponse dispatch(RecordedRequest request) {
            MockResponse response = delegate.dispatch(request);
            if (!"GET".equals(request.getMethod()) || response == null || response.code() != 200 || response.getBody() == null) return response;
            String path = request.getPath().split("\\?", 2)[0];
            String api;
            String plural;
            if ("/api/v1/nodes".equals(path)) {
                api = "v1";
                plural = "nodes";
            } else {
                var match = java.util.regex.Pattern.compile("^/(?:api/(v1)|apis/([^/]+/[^/]+))/namespaces/[^/]+/([^/]+)$").matcher(path);
                if (!match.matches()) return response;
                api = match.group(1) != null ? match.group(1) : match.group(2);
                plural = match.group(3);
            }
            String kind = KINDS.get(api + "/" + plural);
            if (kind == null && api.matches("k8s\\.keycloak\\.org/v[0-9]+(?:(?:alpha|beta)[0-9]+)?") && "keycloaks".equals(plural)) kind = "KeycloakList";
            if (kind == null) return response;
            try {
                var value = JSON.readTree(response.getBody().getBytes());
                if (value instanceof ObjectNode object && "v1".equals(object.path("apiVersion").asText())
                        && "List".equals(object.path("kind").asText()) && object.path("items").isArray()
                        && object.path("metadata").isObject()) {
                    object.put("apiVersion", api);
                    object.put("kind", kind);
                    response.setBody(JSON.writeValueAsBytes(object));
                }
                return response;
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Invalid CRUD-generated fixture JSON", e);
            }
        }

        @Override public void reset() { delegate.reset(); }
        @Override public void expectCustomResource(CustomResourceDefinitionContext definition) { delegate.expectCustomResource(definition); }
        @Override public void shutdown() { delegate.shutdown(); }
        @Override public void releaseResources() { delegate.releaseResources(); }
    }
}
