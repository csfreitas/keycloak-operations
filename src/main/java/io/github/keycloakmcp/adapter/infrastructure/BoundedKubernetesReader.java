package io.github.keycloakmcp.adapter.infrastructure;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.fabric8.kubernetes.api.model.APIGroup;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.http.AsyncBody;
import io.fabric8.kubernetes.client.http.HttpResponse;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.config.v1.ClusterVersion;
import io.fabric8.openshift.api.model.config.v1.Infrastructure;
import io.github.keycloakmcp.collection.CollectionBudget;

/** Fixed read-only Kubernetes resources over the already authorized target client's transport. */
public final class BoundedKubernetesReader {
    static final int MAX_BYTES = 1_048_576;
    static final int MAX_ITEMS = 500;
    static final int MAX_TIMEOUT_MS = 5_000;
    private static final String FAILURE = "Infrastructure response unavailable or incomplete";
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).build();
    static {
        JSON.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(32).maxStringLength(8192).maxNameLength(8192).maxNumberLength(128).build());
    }
    // Fabric8 nested deserializers (for example Quantity) call codec.readTree.
    // Their next sibling is not a second wire document. Conversion only receives
    // trees already parsed in full by JSON, with duplicate/trailing checks intact.
    private static final ObjectMapper MODELS = JSON.copy().disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /** Constructor is private: callers cannot supply arbitrary API paths, kinds or model types. */
    public static final class ResourceType<T extends HasMetadata> {
        private final String apiVersion, kind, plural;
        private final boolean namespaced;
        private final Class<T> model;
        private ResourceType(String apiVersion, String kind, String plural, boolean namespaced, Class<T> model) {
            this.apiVersion = apiVersion; this.kind = kind; this.plural = plural;
            this.namespaced = namespaced; this.model = model;
        }
    }

    public static final ResourceType<Deployment> DEPLOYMENTS = type("apps/v1", "Deployment", "deployments", true, Deployment.class);
    public static final ResourceType<StatefulSet> STATEFUL_SETS = type("apps/v1", "StatefulSet", "statefulsets", true, StatefulSet.class);
    public static final ResourceType<ReplicaSet> REPLICA_SETS = type("apps/v1", "ReplicaSet", "replicasets", true, ReplicaSet.class);
    public static final ResourceType<Pod> PODS = type("v1", "Pod", "pods", true, Pod.class);
    public static final ResourceType<Service> SERVICES = type("v1", "Service", "services", true, Service.class);
    public static final ResourceType<Node> NODES = type("v1", "Node", "nodes", false, Node.class);
    public static final ResourceType<HorizontalPodAutoscaler> HPAS = type("autoscaling/v2", "HorizontalPodAutoscaler", "horizontalpodautoscalers", true, HorizontalPodAutoscaler.class);
    public static final ResourceType<PodDisruptionBudget> PDBS = type("policy/v1", "PodDisruptionBudget", "poddisruptionbudgets", true, PodDisruptionBudget.class);
    public static final ResourceType<Ingress> INGRESSES = type("networking.k8s.io/v1", "Ingress", "ingresses", true, Ingress.class);
    public static final ResourceType<Route> ROUTES = type("route.openshift.io/v1", "Route", "routes", true, Route.class);
    public static final ResourceType<Infrastructure> INFRASTRUCTURES = type("config.openshift.io/v1", "Infrastructure", "infrastructures", false, Infrastructure.class);
    public static final ResourceType<ClusterVersion> CLUSTER_VERSIONS = type("config.openshift.io/v1", "ClusterVersion", "clusterversions", false, ClusterVersion.class);
    public static final ResourceType<GenericKubernetesResource> SERVICE_MONITORS = type("monitoring.coreos.com/v1", "ServiceMonitor", "servicemonitors", true, GenericKubernetesResource.class);

    private BoundedKubernetesReader() { }

    public static ResourceType<GenericKubernetesResource> keycloaks(String apiVersion) {
        require(apiVersion != null && apiVersion.length() <= 128
                && apiVersion.matches("k8s\\.keycloak\\.org/v[0-9]+((alpha|beta)[0-9]+)?"));
        return type(apiVersion, "Keycloak", "keycloaks", true, GenericKubernetesResource.class);
    }

    public static <T extends HasMetadata> List<T> list(KubernetesClient client, ResourceType<T> type, String namespace) {
        return list(client, type, namespace, MAX_ITEMS);
    }

    public static <T extends HasMetadata> List<T> list(KubernetesClient client, ResourceType<T> type, String namespace, int maxItems) {
        require(maxItems > 0 && maxItems <= MAX_ITEMS);
        String path = path(client, type, namespace);
        Reply reply = read(client, path, "limit=" + (maxItems + 1), false);
        try {
            JsonNode root = reply.root();
            envelope(root, type.apiVersion, type.kind + "List");
            JsonNode metadata = root.get("metadata");
            require(metadata != null && metadata.isObject());
            JsonNode continuation = metadata.get("continue");
            require(continuation == null || continuation.isTextual() && continuation.textValue().isEmpty());
            JsonNode remaining = metadata.get("remainingItemCount");
            require(remaining == null || remaining.isIntegralNumber() && remaining.canConvertToLong() && remaining.longValue() == 0);
            JsonNode items = root.get("items");
            require(items != null && items.isArray() && items.size() <= maxItems);
            List<T> result = new ArrayList<>(items.size());
            Set<String> names = new HashSet<>(), uids = new HashSet<>();
            for (JsonNode item : items) {
                reply.checkpoint();
                resource(item, type, namespace, null, true);
                require(names.add(item.path("metadata").path("name").textValue())
                        && uids.add(item.path("metadata").path("uid").textValue()));
                result.add(MODELS.treeToValue(item, type.model));
            }
            reply.checkpoint();
            return List.copyOf(result);
        } catch (CollectionBudget.Aborted e) { throw e; }
        catch (Exception e) { throw invalid(); }
    }

    public static <T extends HasMetadata> T get(KubernetesClient client, ResourceType<T> type, String namespace, String name) {
        require(name(name));
        Reply reply = read(client, path(client, type, namespace) + "/" + name, null, true);
        if (reply == null) return null;
        try {
            resource(reply.root(), type, namespace, name, false);
            T result = MODELS.treeToValue(reply.root(), type.model);
            reply.checkpoint();
            return result;
        } catch (CollectionBudget.Aborted e) { throw e; }
        catch (Exception e) { throw invalid(); }
    }

    public static List<APIGroup> apiGroups(KubernetesClient client) {
        Reply reply = read(client, "/apis", null, false);
        try {
            envelope(reply.root(), "v1", "APIGroupList");
            JsonNode groups = reply.root().get("groups");
            require(groups != null && groups.isArray() && groups.size() <= 100);
            Set<String> seen = new HashSet<>();
            List<APIGroup> result = new ArrayList<>(groups.size());
            for (JsonNode group : groups) {
                reply.checkpoint();
                require(group.isObject() && name(text(group, "name")) && seen.add(text(group, "name")));
                JsonNode versions = group.get("versions");
                require(versions != null && versions.isArray() && !versions.isEmpty() && versions.size() <= 32);
                Set<String> served = new HashSet<>();
                for (JsonNode version : versions) {
                    require(version.isObject() && version(text(version, "version"))
                            && (text(group, "name") + "/" + text(version, "version")).equals(text(version, "groupVersion"))
                            && served.add(text(version, "groupVersion")));
                }
                if (group.has("preferredVersion")) {
                    JsonNode preferred = group.get("preferredVersion");
                    require(preferred.isObject() && served.contains(text(preferred, "groupVersion"))
                            && (text(group, "name") + "/" + text(preferred, "version")).equals(text(preferred, "groupVersion")));
                }
                result.add(MODELS.treeToValue(group, APIGroup.class));
            }
            reply.checkpoint();
            return List.copyOf(result);
        } catch (CollectionBudget.Aborted e) { throw e; }
        catch (Exception e) { throw invalid(); }
    }

    public static String version(KubernetesClient client) {
        Reply reply = read(client, "/version", null, false);
        String version = text(reply.root(), "gitVersion");
        require(version != null && version.length() <= 128 && version.matches("[A-Za-z0-9._+\\-]+"));
        reply.checkpoint();
        return version;
    }

    private static <T extends HasMetadata> ResourceType<T> type(String apiVersion, String kind, String plural, boolean namespaced, Class<T> model) {
        return new ResourceType<>(apiVersion, kind, plural, namespaced, model);
    }

    private static String path(KubernetesClient client, ResourceType<?> type, String namespace) {
        require(client != null && type != null);
        if (type.namespaced) {
            // The caller checks its registered target against ClusterClient.namespace().
            // A client's default namespace is not consulted or used as a fallback.
            require(namespace != null && namespace.length() <= 63 && namespace.matches("[a-z0-9](?:[-a-z0-9]*[a-z0-9])?"));
        } else require(namespace == null);
        return (type.apiVersion.contains("/") ? "/apis/" : "/api/") + type.apiVersion
                + (type.namespaced ? "/namespaces/" + namespace : "") + "/" + type.plural;
    }

    private static Reply read(KubernetesClient client, String path, String query, boolean notFoundIsNull) {
        CollectionBudget budget = CollectionBudget.current();
        if (budget != null) budget.checkpoint();
        require(client != null && !Thread.currentThread().isInterrupted());
        Integer configured = client.getConfiguration().getRequestTimeout();
        long timeoutMs = configured == null || configured <= 0 ? MAX_TIMEOUT_MS : Math.min(configured, MAX_TIMEOUT_MS);
        Duration timeout = Duration.ofMillis(timeoutMs);
        if (budget != null) timeout = budget.cap(timeout);
        if (budget != null) budget.checkpoint();
        long timeoutNanos = timeout.toNanos();
        long deadline = System.nanoTime() + timeoutNanos;
        AtomicReference<AsyncBody> body = new AtomicReference<>();
        AtomicBoolean abandoned = new AtomicBoolean();
        CompletableFuture<HttpResponse<AsyncBody>> pending = null;
        boolean finished = false;
        try {
            URI base = URI.create(client.getConfiguration().getMasterUrl());
            require(base.getHost() != null && base.getRawUserInfo() == null && base.getRawQuery() == null && base.getRawFragment() == null
                    && ("https".equals(base.getScheme()) || "http".equals(base.getScheme())));
            String basePath = base.getRawPath() == null ? "" : base.getRawPath();
            while (basePath.endsWith("/")) basePath = basePath.substring(0, basePath.length() - 1);
            URI uri = URI.create(base.getScheme() + "://" + base.getRawAuthority() + basePath + path
                    + (query == null ? "" : "?" + query));
            var http = client.getHttpClient();
            var request = http.newHttpRequestBuilder().uri(uri).method("GET", null, (String) null)
                    .setHeader("Accept", "application/json").timeout(timeoutNanos, TimeUnit.NANOSECONDS).build();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            pending = http.consumeBytes(request, (buffers, stream) -> {
                checkpoint(deadline, budget);
                for (ByteBuffer buffer : buffers) {
                    require(buffer.remaining() <= MAX_BYTES - bytes.size());
                    byte[] chunk = new byte[buffer.remaining()];
                    buffer.get(chunk);
                    bytes.writeBytes(chunk);
                }
                stream.consume();
            });
            pending.whenComplete((response, failure) -> {
                if (response != null) {
                    body.set(response.body());
                    if (abandoned.get()) cancel(response.body());
                }
            });
            HttpResponse<AsyncBody> response = pending.get(remaining(deadline, budget), TimeUnit.NANOSECONDS);
            body.set(response.body());
            checkpoint(deadline, budget);
            require(response.previousResponse().isEmpty());
            int code = response.code();
            if (code == 404 && notFoundIsNull) return null;
            if (code == 401 || code == 403 || code == 404) throw new KubernetesClientException(FAILURE, code, null);
            require(code == 200);
            String length = response.header("Content-Length");
            if (length != null) {
                long declared = Long.parseLong(length);
                require(declared >= 0 && declared <= MAX_BYTES);
            }
            response.body().consume();
            response.body().done().get(remaining(deadline, budget), TimeUnit.NANOSECONDS);
            checkpoint(deadline, budget);
            JsonNode root = JSON.readTree(bytes.toByteArray());
            require(root != null && root.isObject());
            structure(root);
            checkpoint(deadline, budget);
            finished = true;
            return new Reply(root, deadline, budget);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (budget != null) budget.checkpoint();
            throw invalid();
        } catch (CollectionBudget.Aborted e) {
            throw e;
        } catch (KubernetesClientException e) {
            // Only locally constructed status-only exceptions can escape.
            int code = e.getCode();
            if (code == 401 || code == 403 || code == 404) throw new KubernetesClientException(FAILURE, code, null);
            throw invalid();
        } catch (Exception e) {
            if (budget != null) budget.checkpoint();
            throw invalid();
        }
        finally {
            if (!finished) {
                abandoned.set(true);
                if (pending != null) pending.cancel(true);
                cancel(body.get());
            }
        }
    }

    private record Reply(JsonNode root, long deadline, CollectionBudget budget) {
        void checkpoint() { BoundedKubernetesReader.checkpoint(deadline, budget); }
    }

    private static void envelope(JsonNode root, String apiVersion, String kind) {
        require(root != null && root.isObject() && apiVersion.equals(text(root, "apiVersion")) && kind.equals(text(root, "kind")));
    }

    private static void resource(JsonNode item, ResourceType<?> type, String namespace, String expectedName, boolean typedListItem) {
        require(item != null && item.isObject());
        // Kubernetes typed-list items may omit TypeMeta. Only the already validated
        // outer envelope supplies these two absent fields; explicit null/conflicts
        // and incomplete standalone GET objects are never repaired.
        if (typedListItem && !item.has("apiVersion")) ((ObjectNode) item).put("apiVersion", type.apiVersion);
        if (typedListItem && !item.has("kind")) ((ObjectNode) item).put("kind", type.kind);
        envelope(item, type.apiVersion, type.kind);
        JsonNode metadata = item.get("metadata");
        require(metadata != null && metadata.isObject() && name(text(metadata, "name"))
                && (expectedName == null || expectedName.equals(text(metadata, "name"))));
        String uid = text(metadata, "uid");
        require(uid != null && uid.length() <= 128 && uid.matches("[A-Za-z0-9._:\\-]+"));
        if (type.namespaced) require(namespace.equals(text(metadata, "namespace")));
        else require(!metadata.has("namespace") || "".equals(text(metadata, "namespace")));
        if (metadata.has("labels")) {
            JsonNode labels = metadata.get("labels");
            require(labels.isObject() && labels.size() <= 256);
            labels.fields().forEachRemaining(entry -> require(entry.getKey().length() <= 317
                    && !entry.getKey().isBlank() && entry.getValue().isTextual() && entry.getValue().textValue().length() <= 63));
        }
    }

    private static void structure(JsonNode value) {
        if (value.isContainerNode()) {
            require(value.size() <= 1024);
            value.forEach(BoundedKubernetesReader::structure);
        }
    }

    private static String text(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }
    private static boolean name(String value) {
        if (value == null || value.length() > 253) return false;
        for (String label : value.split("\\.", -1)) {
            if (!version(label)) return false;
        }
        return true;
    }
    private static boolean version(String value) { return value != null && value.length() <= 63 && value.matches("[a-z0-9](?:[-a-z0-9]*[a-z0-9])?"); }
    private static long remaining(long deadline, CollectionBudget budget) {
        checkpoint(deadline, budget);
        long remaining = Math.max(1, deadline - System.nanoTime());
        return budget == null ? remaining : Math.min(remaining, Math.max(1, budget.remaining().toNanos()));
    }
    private static void checkpoint(long deadline, CollectionBudget budget) {
        if (budget != null) budget.checkpoint();
        require(!Thread.currentThread().isInterrupted() && deadline - System.nanoTime() > 0);
    }
    private static void cancel(AsyncBody body) {
        if (body != null) {
            try { body.cancel(); } catch (RuntimeException ignored) { /* Never expose transport diagnostics on cancellation. */ }
        }
    }
    private static void require(boolean valid) { if (!valid) throw invalid(); }
    private static IllegalStateException invalid() { return new IllegalStateException(FAILURE); }
}
