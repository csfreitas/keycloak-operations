package io.github.keycloakmcp.service.platform;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.adapter.infrastructure.InfrastructureClientFactory;
import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.discovery.EnvironmentDiscovery;
import io.github.keycloakmcp.discovery.EnvironmentInfo;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.inventory.ClusterInfo;
import io.github.keycloakmcp.domain.inventory.CollectionWarning;
import io.github.keycloakmcp.domain.inventory.DeploymentMethod;
import io.github.keycloakmcp.domain.inventory.HpaInfo;
import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
import io.github.keycloakmcp.domain.inventory.KeycloakWorkloadInfo;
import io.github.keycloakmcp.domain.inventory.NetworkingInfo;
import io.github.keycloakmcp.domain.inventory.PdbInfo;
import io.github.keycloakmcp.domain.inventory.PodInventoryItem;
import io.github.keycloakmcp.domain.inventory.ProbeInfo;
import io.github.keycloakmcp.domain.inventory.ResourceConfig;
import io.github.keycloakmcp.domain.inventory.SchedulingInfo;
import io.github.keycloakmcp.domain.inventory.TopologyInfo;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Collects a sanitized {@link InfrastructureInventory} for a registered Target.
 * <p>
 * Partial failures become {@link CollectionWarning}s; independent sections continue.
 * Secrets and env vars are never included.
 */
@ApplicationScoped
public class InventoryService {

    private static final Logger LOG = Logger.getLogger(InventoryService.class);
    private static final String ZONE_LABEL = "topology.kubernetes.io/zone";
    private static final String KEYCLOAK_CR_GROUP = "k8s.keycloak.org";
    private static final String KEYCLOAK_CR_VERSION = "v2alpha1";
    private static final String KEYCLOAK_CR_PLURAL = "keycloaks";

    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final InfrastructureClientFactory clientFactory;
    private final EnvironmentDiscovery environmentDiscovery;

    @Inject
    public InventoryService(
            TargetResolver targetResolver,
            TargetAuthorizationService targetAuthorization,
            InfrastructureClientFactory clientFactory,
            EnvironmentDiscovery environmentDiscovery) {
        this.targetResolver = targetResolver;
        this.targetAuthorization = targetAuthorization;
        this.clientFactory = clientFactory;
        this.environmentDiscovery = environmentDiscovery;
    }

    public InfrastructureInventory collect(String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);

        if (!target.hasInfrastructure()
                || target.infrastructureTypeOrNone() == InfrastructureType.NONE
                || target.infrastructureTypeOrNone() == InfrastructureType.VM) {
            Instant now = Instant.now();
            return new InfrastructureInventory(
                    targetId,
                    "UNKNOWN",
                    new ClusterInfo(null, null, null, -1, -1),
                    KeycloakWorkloadInfo.unknown(null),
                    List.of(),
                    new TopologyInfo(Map.of(), Map.of(), 0),
                    new SchedulingInfo(false, false),
                    HpaInfo.absent(),
                    PdbInfo.absent(),
                    new ResourceConfig(null, null, null, null),
                    ProbeInfo.unknown(),
                    new NetworkingInfo(false, null, false),
                    List.of(new CollectionWarning(
                            CollectionWarning.WarningCode.NOT_CONFIGURED,
                            "infrastructure",
                            "Target has no OpenShift/Kubernetes infrastructure binding")),
                    now);
        }

        Optional<ClusterClient> clientOpt = clientFactory.resolve(target);
        if (clientOpt.isEmpty()) {
            throw McpException.unsupportedCapability(
                    "Infrastructure client unavailable for target '" + targetId + "'");
        }

        ClusterClient clusterClient = clientOpt.get();
        KubernetesClient k8s = clusterClient.kubernetes();
        String namespace = resolveNamespace(target, clusterClient);
        List<CollectionWarning> warnings = new ArrayList<>();

        EnvironmentInfo env = environmentDiscovery.discover(target);
        String runtime = env.runtime() == null ? "UNKNOWN" : env.runtime().name();

        ClusterInfo cluster = collectCluster(k8s, clusterClient, env, warnings);
        WorkloadRef workload = findWorkload(k8s, namespace, warnings);
        KeycloakWorkloadInfo keycloak = toWorkloadInfo(workload, namespace);
        List<PodInventoryItem> pods = collectPods(k8s, namespace, workload, warnings);
        TopologyInfo topology = buildTopology(pods);
        SchedulingInfo scheduling = collectScheduling(workload);
        HpaInfo hpa = collectHpa(k8s, namespace, workload, warnings);
        PdbInfo pdb = collectPdb(k8s, namespace, workload, warnings);
        ResourceConfig resources = collectResources(workload);
        ProbeInfo probes = collectProbes(workload);
        NetworkingInfo networking = collectNetworking(k8s, clusterClient, namespace, warnings);

        return new InfrastructureInventory(
                targetId,
                runtime,
                cluster,
                keycloak,
                List.copyOf(pods),
                topology,
                scheduling,
                hpa,
                pdb,
                resources,
                probes,
                networking,
                List.copyOf(warnings),
                Instant.now());
    }

    /**
     * Converts inventory into assessment Evidence keys (all stamped with targetId).
     */
    public List<Evidence> toEvidence(InfrastructureInventory inventory) {
        String targetId = inventory.targetId();
        Instant now = inventory.collectedAt() == null ? Instant.now() : inventory.collectedAt();
        List<Evidence> out = new ArrayList<>();
        add(out, targetId, "runtime", "runtime.type", inventory.runtime(), now);
        ClusterInfo c = inventory.cluster();
        if (c != null) {
            add(out, targetId, "cluster", "cluster.distribution", c.distribution(), now);
            add(out, targetId, "cluster", "cluster.version", c.version(), now);
            add(out, targetId, "cluster", "cluster.platform", c.platform(), now);
            if (c.nodeCount() >= 0) {
                add(out, targetId, "cluster", "cluster.nodes.count", c.nodeCount(), now);
            }
            if (c.zoneCount() >= 0) {
                add(out, targetId, "cluster", "cluster.zones.count", c.zoneCount(), now);
            }
        }
        KeycloakWorkloadInfo kc = inventory.keycloak();
        if (kc != null) {
            add(out, targetId, "workload", "keycloak.deployment.method",
                    kc.deploymentMethod() == null ? null : kc.deploymentMethod().name(), now);
            if (kc.desiredReplicas() >= 0) {
                add(out, targetId, "workload", "keycloak.replicas.desired", kc.desiredReplicas(), now);
                add(out, targetId, "workload", "deployment.replicas", kc.desiredReplicas(), now);
            }
            if (kc.readyReplicas() >= 0) {
                add(out, targetId, "workload", "keycloak.replicas.ready", kc.readyReplicas(), now);
            }
            if (kc.desiredReplicas() >= 0 && kc.readyReplicas() >= 0) {
                add(out, targetId, "workload", "keycloak.replicas.readyBelowDesired",
                        kc.readyReplicas() < kc.desiredReplicas(), now);
            }
        }
        List<PodInventoryItem> pods = inventory.pods() == null ? List.of() : inventory.pods();
        add(out, targetId, "pods", "keycloak.pods.total", pods.size(), now);
        long ready = pods.stream().filter(PodInventoryItem::ready).count();
        add(out, targetId, "pods", "keycloak.pods.ready", ready, now);
        int restarts = pods.stream().mapToInt(PodInventoryItem::restartCount).sum();
        add(out, targetId, "pods", "keycloak.pods.restartCount", restarts, now);
        long oom = pods.stream().filter(PodInventoryItem::oomKilled).count();
        add(out, targetId, "pods", "keycloak.pods.oomKilledCount", oom, now);

        TopologyInfo topo = inventory.topology();
        if (topo != null) {
            add(out, targetId, "topology", "keycloak.topology.zoneCount", topo.zoneCount(), now);
            add(out, targetId, "topology", "keycloak.topology.podsByZone", topo.podsByZone(), now);
            add(out, targetId, "topology", "keycloak.topology.podsByNode", topo.podsByNode(), now);
            add(out, targetId, "topology", "keycloak.topology.singleZoneConcentration",
                    isSingleBucketConcentration(topo.podsByZone(), topo.zoneCount()), now);
            add(out, targetId, "topology", "keycloak.topology.singleNodeConcentration",
                    isSingleBucketConcentration(topo.podsByNode(), -1), now);
        }
        SchedulingInfo sched = inventory.scheduling();
        if (sched != null) {
            add(out, targetId, "scheduling", "keycloak.scheduling.zoneSpread.present",
                    sched.zoneSpreadPresent(), now);
            add(out, targetId, "scheduling", "keycloak.scheduling.hostnameSpread.present",
                    sched.hostnameSpreadPresent(), now);
        }
        HpaInfo hpa = inventory.hpa();
        if (hpa != null) {
            add(out, targetId, "autoscaling", "keycloak.hpa.present", hpa.present(), now);
            if (hpa.present()) {
                add(out, targetId, "autoscaling", "keycloak.hpa.minReplicas", hpa.minReplicas(), now);
                add(out, targetId, "autoscaling", "keycloak.hpa.maxReplicas", hpa.maxReplicas(), now);
            }
        }
        PdbInfo pdb = inventory.pdb();
        if (pdb != null) {
            add(out, targetId, "disruption", "keycloak.pdb.present", pdb.present(), now);
        }
        ResourceConfig res = inventory.resources();
        if (res != null) {
            add(out, targetId, "resources", "keycloak.resources.requests.cpu", res.requestsCpu(), now);
            add(out, targetId, "resources", "keycloak.resources.requests.memory", res.requestsMemory(), now);
            add(out, targetId, "resources", "keycloak.resources.limits.cpu", res.limitsCpu(), now);
            add(out, targetId, "resources", "keycloak.resources.limits.memory", res.limitsMemory(), now);
            add(out, targetId, "resources", "keycloak.resources.requests.cpu.present",
                    res.requestsCpu() != null && !res.requestsCpu().isBlank(), now);
            add(out, targetId, "resources", "keycloak.resources.requests.memory.present",
                    res.requestsMemory() != null && !res.requestsMemory().isBlank(), now);
            add(out, targetId, "resources", "keycloak.resources.limits.memory.present",
                    res.limitsMemory() != null && !res.limitsMemory().isBlank(), now);
        }
        ProbeInfo probes = inventory.probes();
        if (probes != null) {
            add(out, targetId, "probes", "keycloak.probes.readiness.present", probes.readinessPresent(), now);
            add(out, targetId, "probes", "keycloak.probes.liveness.present", probes.livenessPresent(), now);
            add(out, targetId, "probes", "keycloak.probes.startup.present", probes.startupPresent(), now);
        }
        NetworkingInfo net = inventory.networking();
        if (net != null) {
            add(out, targetId, "networking", "keycloak.route.present", net.routeOrIngressPresent(), now);
        }
        if (inventory.warnings() != null) {
            for (CollectionWarning w : inventory.warnings()) {
                add(out, targetId, "collection", "collection.warning." + w.resource(), w.code().name(), now);
            }
        }
        return List.copyOf(out);
    }

    private static void add(
            List<Evidence> out, String targetId, String category, String key, Object value, Instant now) {
        if (value == null) {
            return;
        }
        out.add(new Evidence(targetId, "infrastructure", category, key, value, now));
    }

    /**
     * True when pods exist and all sit in a single bucket while multiple buckets are expected
     * (zoneCount &gt; 1) or, for nodes, whenever more than one pod shares one node exclusively.
     */
    private static boolean isSingleBucketConcentration(Map<String, Integer> buckets, int expectedBuckets) {
        if (buckets == null || buckets.isEmpty()) {
            return false;
        }
        int total = buckets.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 1) {
            return false;
        }
        long nonEmpty = buckets.values().stream().filter(v -> v != null && v > 0).count();
        if (nonEmpty != 1) {
            return false;
        }
        if (expectedBuckets > 1) {
            return true;
        }
        // nodes: concentration if all pods on one node and there is more than one pod
        return expectedBuckets < 0;
    }

    private String resolveNamespace(Target target, ClusterClient client) {
        if (target.infrastructure() != null
                && target.infrastructure().namespace() != null
                && !target.infrastructure().namespace().isBlank()) {
            return target.infrastructure().namespace();
        }
        return client.namespace();
    }

    private ClusterInfo collectCluster(
            KubernetesClient k8s,
            ClusterClient clusterClient,
            EnvironmentInfo env,
            List<CollectionWarning> warnings) {
        String distribution = clusterClient.type() == InfrastructureType.OPENSHIFT ? "openshift" : "kubernetes";
        String version = env.clusterVersion();
        String platform = null;
        int nodeCount = -1;
        int zoneCount = -1;

        try {
            List<Node> nodes = k8s.nodes().list().getItems();
            nodeCount = nodes.size();
            Map<String, Integer> zones = new LinkedHashMap<>();
            for (Node node : nodes) {
                String zone = label(node.getMetadata() == null ? null : node.getMetadata().getLabels(), ZONE_LABEL);
                if (zone != null) {
                    zones.merge(zone, 1, Integer::sum);
                }
            }
            zoneCount = zones.isEmpty() ? 0 : zones.size();
        } catch (KubernetesClientException e) {
            warnings.add(mapClientException("nodes", e));
        } catch (RuntimeException e) {
            warnings.add(CollectionWarning.collectionFailed("nodes", e.getMessage()));
        }

        if (clusterClient.type() == InfrastructureType.OPENSHIFT) {
            try {
                Optional<OpenShiftClient> oc = clusterClient.openshift();
                if (oc.isPresent()) {
                    platform = readOpenShiftPlatform(oc.get(), warnings);
                    String ocpVersion = readOpenShiftVersion(oc.get(), warnings);
                    if (ocpVersion != null) {
                        version = ocpVersion;
                    }
                }
            } catch (RuntimeException e) {
                warnings.add(CollectionWarning.collectionFailed("openshift-config", e.getMessage()));
            }
        }

        return new ClusterInfo(distribution, version, platform, nodeCount, zoneCount);
    }

    private String readOpenShiftPlatform(OpenShiftClient oc, List<CollectionWarning> warnings) {
        try {
            var infra = oc.config().infrastructures().withName("cluster").get();
            if (infra != null && infra.getStatus() != null && infra.getStatus().getPlatform() != null) {
                return infra.getStatus().getPlatform();
            }
        } catch (KubernetesClientException e) {
            warnings.add(mapClientException("infrastructure", e));
        } catch (RuntimeException e) {
            warnings.add(CollectionWarning.collectionFailed("infrastructure", e.getMessage()));
        }
        return null;
    }

    private String readOpenShiftVersion(OpenShiftClient oc, List<CollectionWarning> warnings) {
        try {
            var cv = oc.config().clusterVersions().withName("version").get();
            if (cv != null && cv.getStatus() != null && cv.getStatus().getDesired() != null) {
                return cv.getStatus().getDesired().getVersion();
            }
        } catch (KubernetesClientException e) {
            warnings.add(mapClientException("clusterversion", e));
        } catch (RuntimeException e) {
            warnings.add(CollectionWarning.collectionFailed("clusterversion", e.getMessage()));
        }
        return null;
    }

    private WorkloadRef findWorkload(KubernetesClient k8s, String namespace, List<CollectionWarning> warnings) {
        if (namespace == null || namespace.isBlank()) {
            warnings.add(new CollectionWarning(
                    CollectionWarning.WarningCode.NOT_CONFIGURED,
                    "namespace",
                    "No namespace configured on target infrastructure"));
            return WorkloadRef.none();
        }

        // 1) Keycloak Operator CR
        try {
            ResourceDefinitionContext ctx = new ResourceDefinitionContext.Builder()
                    .withGroup(KEYCLOAK_CR_GROUP)
                    .withVersion(KEYCLOAK_CR_VERSION)
                    .withPlural(KEYCLOAK_CR_PLURAL)
                    .withNamespaced(true)
                    .build();
            List<GenericKubernetesResource> crs = k8s.genericKubernetesResources(ctx)
                    .inNamespace(namespace)
                    .list()
                    .getItems();
            if (!crs.isEmpty()) {
                GenericKubernetesResource cr = crs.get(0);
                String name = cr.getMetadata() != null ? cr.getMetadata().getName() : null;
                int instances = readCrInstances(cr);
                return new WorkloadRef(DeploymentMethod.KEYCLOAK_OPERATOR, name, instances, instances, instances,
                        instances, null, null, cr);
            }
        } catch (KubernetesClientException e) {
            if (e.getCode() != 404) {
                warnings.add(mapClientException("keycloak-cr", e));
            }
        } catch (RuntimeException e) {
            warnings.add(CollectionWarning.collectionFailed("keycloak-cr", e.getMessage()));
        }

        // 2) Deployment heuristic
        try {
            List<Deployment> deployments = k8s.apps().deployments().inNamespace(namespace).list().getItems();
            Optional<Deployment> match = deployments.stream().filter(this::looksLikeKeycloak).findFirst();
            if (match.isPresent()) {
                Deployment d = match.get();
                var status = d.getStatus();
                int desired = d.getSpec() != null && d.getSpec().getReplicas() != null
                        ? d.getSpec().getReplicas() : -1;
                int ready = status != null && status.getReadyReplicas() != null ? status.getReadyReplicas() : -1;
                int current = status != null && status.getReplicas() != null ? status.getReplicas() : -1;
                int available = status != null && status.getAvailableReplicas() != null
                        ? status.getAvailableReplicas() : -1;
                PodSpec podSpec = d.getSpec() != null && d.getSpec().getTemplate() != null
                        ? d.getSpec().getTemplate().getSpec() : null;
                Map<String, String> selector = d.getSpec() != null && d.getSpec().getSelector() != null
                        ? d.getSpec().getSelector().getMatchLabels() : Map.of();
                return new WorkloadRef(DeploymentMethod.DEPLOYMENT, d.getMetadata().getName(),
                        desired, ready, current, available, podSpec, selector, null);
            }
        } catch (KubernetesClientException e) {
            warnings.add(mapClientException("deployments", e));
        } catch (RuntimeException e) {
            warnings.add(CollectionWarning.collectionFailed("deployments", e.getMessage()));
        }

        // 3) StatefulSet heuristic
        try {
            List<StatefulSet> sets = k8s.apps().statefulSets().inNamespace(namespace).list().getItems();
            Optional<StatefulSet> match = sets.stream().filter(this::looksLikeKeycloakSts).findFirst();
            if (match.isPresent()) {
                StatefulSet s = match.get();
                var status = s.getStatus();
                int desired = s.getSpec() != null && s.getSpec().getReplicas() != null
                        ? s.getSpec().getReplicas() : -1;
                int ready = status != null && status.getReadyReplicas() != null ? status.getReadyReplicas() : -1;
                int current = status != null && status.getReplicas() != null ? status.getReplicas() : -1;
                int available = status != null && status.getAvailableReplicas() != null
                        ? status.getAvailableReplicas() : -1;
                PodSpec podSpec = s.getSpec() != null && s.getSpec().getTemplate() != null
                        ? s.getSpec().getTemplate().getSpec() : null;
                Map<String, String> selector = s.getSpec() != null && s.getSpec().getSelector() != null
                        ? s.getSpec().getSelector().getMatchLabels() : Map.of();
                return new WorkloadRef(DeploymentMethod.STATEFULSET, s.getMetadata().getName(),
                        desired, ready, current, available, podSpec, selector, null);
            }
        } catch (KubernetesClientException e) {
            warnings.add(mapClientException("statefulsets", e));
        } catch (RuntimeException e) {
            warnings.add(CollectionWarning.collectionFailed("statefulsets", e.getMessage()));
        }

        warnings.add(new CollectionWarning(
                CollectionWarning.WarningCode.RESOURCE_NOT_FOUND,
                "keycloak-workload",
                "No Keycloak Operator CR, Deployment, or StatefulSet matched in namespace " + namespace));
        return WorkloadRef.none();
    }

    private boolean looksLikeKeycloak(Deployment d) {
        String name = d.getMetadata() != null ? d.getMetadata().getName() : "";
        Map<String, String> labels = d.getMetadata() != null ? d.getMetadata().getLabels() : Map.of();
        return matchesKeycloakIdentity(name, labels);
    }

    private boolean looksLikeKeycloakSts(StatefulSet s) {
        String name = s.getMetadata() != null ? s.getMetadata().getName() : "";
        Map<String, String> labels = s.getMetadata() != null ? s.getMetadata().getLabels() : Map.of();
        return matchesKeycloakIdentity(name, labels);
    }

    private boolean matchesKeycloakIdentity(String name, Map<String, String> labels) {
        String n = name == null ? "" : name.toLowerCase();
        if (n.contains("keycloak") || n.contains("rhbk")) {
            return true;
        }
        if (labels == null) {
            return false;
        }
        for (Map.Entry<String, String> e : labels.entrySet()) {
            String k = e.getKey() == null ? "" : e.getKey().toLowerCase();
            String v = e.getValue() == null ? "" : e.getValue().toLowerCase();
            if (k.contains("keycloak") || v.contains("keycloak") || v.equals("rhbk") || v.contains("rhbk")) {
                return true;
            }
            if (("app".equals(k) || "app.kubernetes.io/name".equals(k))
                    && (v.contains("keycloak") || v.contains("rhbk"))) {
                return true;
            }
        }
        return false;
    }

    private int readCrInstances(GenericKubernetesResource cr) {
        Object spec = cr.get("spec");
        if (spec instanceof Map<?, ?> map) {
            Object instances = map.get("instances");
            if (instances instanceof Number number) {
                return number.intValue();
            }
        }
        return -1;
    }

    private KeycloakWorkloadInfo toWorkloadInfo(WorkloadRef workload, String namespace) {
        if (workload.method() == DeploymentMethod.UNKNOWN) {
            return KeycloakWorkloadInfo.unknown(namespace);
        }
        return new KeycloakWorkloadInfo(
                workload.method(),
                namespace,
                workload.name(),
                workload.desired(),
                workload.ready(),
                workload.current(),
                workload.available());
    }

    private List<PodInventoryItem> collectPods(
            KubernetesClient k8s,
            String namespace,
            WorkloadRef workload,
            List<CollectionWarning> warnings) {
        if (namespace == null || namespace.isBlank()) {
            return List.of();
        }
        try {
            List<Pod> pods;
            if (workload.selector() != null && !workload.selector().isEmpty()) {
                pods = k8s.pods().inNamespace(namespace).withLabels(workload.selector()).list().getItems();
            } else {
                pods = k8s.pods().inNamespace(namespace).list().getItems().stream()
                        .filter(p -> matchesKeycloakIdentity(
                                p.getMetadata() != null ? p.getMetadata().getName() : "",
                                p.getMetadata() != null ? p.getMetadata().getLabels() : Map.of()))
                        .toList();
            }

            Map<String, String> nodeZones = new LinkedHashMap<>();
            try {
                for (Node node : k8s.nodes().list().getItems()) {
                    String nodeName = node.getMetadata() != null ? node.getMetadata().getName() : null;
                    if (nodeName != null) {
                        nodeZones.put(nodeName,
                                label(node.getMetadata().getLabels(), ZONE_LABEL));
                    }
                }
            } catch (RuntimeException e) {
                LOG.debugf(e, "Unable to map node zones while collecting pods");
            }

            List<PodInventoryItem> items = new ArrayList<>();
            for (Pod pod : pods) {
                String name = pod.getMetadata() != null ? pod.getMetadata().getName() : null;
                String nodeName = pod.getSpec() != null ? pod.getSpec().getNodeName() : null;
                String zone = nodeName == null ? null : nodeZones.get(nodeName);
                boolean ready = isPodReady(pod);
                int restarts = restartCount(pod);
                boolean oom = isOomKilled(pod);
                items.add(new PodInventoryItem(name, nodeName, zone, ready, restarts, oom));
            }
            return items;
        } catch (KubernetesClientException e) {
            warnings.add(mapClientException("pods", e));
            return List.of();
        } catch (RuntimeException e) {
            warnings.add(CollectionWarning.collectionFailed("pods", e.getMessage()));
            return List.of();
        }
    }

    private static boolean isPodReady(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getConditions() == null) {
            return false;
        }
        return pod.getStatus().getConditions().stream()
                .anyMatch(c -> "Ready".equals(c.getType()) && "True".equalsIgnoreCase(c.getStatus()));
    }

    private static int restartCount(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
            return 0;
        }
        int total = 0;
        for (ContainerStatus status : pod.getStatus().getContainerStatuses()) {
            if (status.getRestartCount() != null) {
                total += status.getRestartCount();
            }
        }
        return total;
    }

    private static boolean isOomKilled(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
            return false;
        }
        for (ContainerStatus status : pod.getStatus().getContainerStatuses()) {
            if (status.getLastState() != null
                    && status.getLastState().getTerminated() != null
                    && "OOMKilled".equalsIgnoreCase(status.getLastState().getTerminated().getReason())) {
                return true;
            }
        }
        return false;
    }

    private TopologyInfo buildTopology(List<PodInventoryItem> pods) {
        Map<String, Integer> byZone = new LinkedHashMap<>();
        Map<String, Integer> byNode = new LinkedHashMap<>();
        for (PodInventoryItem pod : pods) {
            if (pod.zone() != null && !pod.zone().isBlank()) {
                byZone.merge(pod.zone(), 1, Integer::sum);
            }
            if (pod.nodeName() != null && !pod.nodeName().isBlank()) {
                byNode.merge(pod.nodeName(), 1, Integer::sum);
            }
        }
        return new TopologyInfo(Map.copyOf(byZone), Map.copyOf(byNode), byZone.size());
    }

    private SchedulingInfo collectScheduling(WorkloadRef workload) {
        PodSpec spec = workload.podSpec();
        if (spec == null || spec.getTopologySpreadConstraints() == null) {
            return new SchedulingInfo(false, false);
        }
        boolean zone = false;
        boolean hostname = false;
        for (var tsc : spec.getTopologySpreadConstraints()) {
            String key = tsc.getTopologyKey();
            if (ZONE_LABEL.equals(key)) {
                zone = true;
            }
            if ("kubernetes.io/hostname".equals(key)) {
                hostname = true;
            }
        }
        return new SchedulingInfo(zone, hostname);
    }

    private HpaInfo collectHpa(
            KubernetesClient k8s, String namespace, WorkloadRef workload, List<CollectionWarning> warnings) {
        if (namespace == null || workload.name() == null) {
            return HpaInfo.absent();
        }
        try {
            List<HorizontalPodAutoscaler> hpas =
                    k8s.autoscaling().v2().horizontalPodAutoscalers().inNamespace(namespace).list().getItems();
            for (HorizontalPodAutoscaler hpa : hpas) {
                if (hpa.getSpec() == null || hpa.getSpec().getScaleTargetRef() == null) {
                    continue;
                }
                String refName = hpa.getSpec().getScaleTargetRef().getName();
                if (workload.name().equals(refName)) {
                    int min = hpa.getSpec().getMinReplicas() == null ? -1 : hpa.getSpec().getMinReplicas();
                    int max = hpa.getSpec().getMaxReplicas() == null ? -1 : hpa.getSpec().getMaxReplicas();
                    int current = hpa.getStatus() != null && hpa.getStatus().getCurrentReplicas() != null
                            ? hpa.getStatus().getCurrentReplicas() : -1;
                    return new HpaInfo(true, min, max, current);
                }
            }
            return HpaInfo.absent();
        } catch (KubernetesClientException e) {
            warnings.add(mapClientException("hpa", e));
            return HpaInfo.absent();
        } catch (RuntimeException e) {
            warnings.add(CollectionWarning.collectionFailed("hpa", e.getMessage()));
            return HpaInfo.absent();
        }
    }

    private PdbInfo collectPdb(
            KubernetesClient k8s, String namespace, WorkloadRef workload, List<CollectionWarning> warnings) {
        if (namespace == null) {
            return PdbInfo.absent();
        }
        try {
            List<PodDisruptionBudget> pdbs =
                    k8s.policy().v1().podDisruptionBudget().inNamespace(namespace).list().getItems();
            for (PodDisruptionBudget pdb : pdbs) {
                if (pdb.getSpec() == null) {
                    continue;
                }
                // Prefer selector match against workload labels when available
                boolean related = workload.selector() == null || workload.selector().isEmpty()
                        || selectorsOverlap(workload.selector(),
                        pdb.getSpec().getSelector() == null
                                ? Map.of()
                                : pdb.getSpec().getSelector().getMatchLabels());
                if (!related && workload.name() != null
                        && pdb.getMetadata() != null
                        && pdb.getMetadata().getName() != null
                        && !pdb.getMetadata().getName().toLowerCase().contains("keycloak")
                        && !pdb.getMetadata().getName().toLowerCase().contains("rhbk")) {
                    continue;
                }
                String minAvailable = pdb.getSpec().getMinAvailable() == null
                        ? null : pdb.getSpec().getMinAvailable().toString();
                String maxUnavailable = pdb.getSpec().getMaxUnavailable() == null
                        ? null : pdb.getSpec().getMaxUnavailable().toString();
                return new PdbInfo(true, minAvailable, maxUnavailable);
            }
            return PdbInfo.absent();
        } catch (KubernetesClientException e) {
            warnings.add(mapClientException("pdb", e));
            return PdbInfo.absent();
        } catch (RuntimeException e) {
            warnings.add(CollectionWarning.collectionFailed("pdb", e.getMessage()));
            return PdbInfo.absent();
        }
    }

    private static boolean selectorsOverlap(Map<String, String> a, Map<String, String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, String> e : a.entrySet()) {
            if (e.getValue() != null && e.getValue().equals(b.get(e.getKey()))) {
                return true;
            }
        }
        return false;
    }

    private ResourceConfig collectResources(WorkloadRef workload) {
        Container container = primaryContainer(workload.podSpec());
        if (container == null) {
            return new ResourceConfig(null, null, null, null);
        }
        ResourceRequirements rr = container.getResources();
        if (rr == null) {
            return new ResourceConfig(null, null, null, null);
        }
        return new ResourceConfig(
                quantity(rr.getRequests(), "cpu"),
                quantity(rr.getRequests(), "memory"),
                quantity(rr.getLimits(), "cpu"),
                quantity(rr.getLimits(), "memory"));
    }

    private ProbeInfo collectProbes(WorkloadRef workload) {
        Container container = primaryContainer(workload.podSpec());
        if (container == null) {
            return ProbeInfo.unknown();
        }
        return new ProbeInfo(
                container.getReadinessProbe() != null,
                container.getLivenessProbe() != null,
                container.getStartupProbe() != null);
    }

    private static Container primaryContainer(PodSpec spec) {
        if (spec == null || spec.getContainers() == null || spec.getContainers().isEmpty()) {
            return null;
        }
        return spec.getContainers().stream()
                .filter(c -> c.getName() != null && c.getName().toLowerCase().contains("keycloak"))
                .findFirst()
                .orElse(spec.getContainers().get(0));
    }

    private static String quantity(Map<String, Quantity> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        return map.get(key).toString();
    }

    private NetworkingInfo collectNetworking(
            KubernetesClient k8s,
            ClusterClient clusterClient,
            String namespace,
            List<CollectionWarning> warnings) {
        if (namespace == null) {
            return new NetworkingInfo(false, null, false);
        }

        if (clusterClient.type() == InfrastructureType.OPENSHIFT) {
            try {
                Optional<OpenShiftClient> oc = clusterClient.openshift();
                if (oc.isPresent()) {
                    List<Route> routes = oc.get().routes().inNamespace(namespace).list().getItems();
                    Optional<Route> route = routes.stream()
                            .filter(r -> {
                                String n = r.getMetadata() != null ? r.getMetadata().getName() : "";
                                return matchesKeycloakIdentity(n,
                                        r.getMetadata() != null ? r.getMetadata().getLabels() : Map.of());
                            })
                            .findFirst()
                            .or(() -> routes.stream().findFirst());
                    if (route.isPresent()) {
                        String host = route.get().getSpec() != null ? route.get().getSpec().getHost() : null;
                        boolean tls = route.get().getSpec() != null && route.get().getSpec().getTls() != null;
                        return new NetworkingInfo(true, host, tls);
                    }
                }
            } catch (KubernetesClientException e) {
                warnings.add(mapClientException("routes", e));
            } catch (RuntimeException e) {
                warnings.add(CollectionWarning.collectionFailed("routes", e.getMessage()));
            }
        }

        try {
            List<Ingress> ingresses =
                    k8s.network().v1().ingresses().inNamespace(namespace).list().getItems();
            Optional<Ingress> ingress = ingresses.stream()
                    .filter(i -> matchesKeycloakIdentity(
                            i.getMetadata() != null ? i.getMetadata().getName() : "",
                            i.getMetadata() != null ? i.getMetadata().getLabels() : Map.of()))
                    .findFirst()
                    .or(() -> ingresses.stream().findFirst());
            if (ingress.isPresent() && ingress.get().getSpec() != null
                    && ingress.get().getSpec().getRules() != null
                    && !ingress.get().getSpec().getRules().isEmpty()) {
                String host = ingress.get().getSpec().getRules().get(0).getHost();
                boolean tls = ingress.get().getSpec().getTls() != null
                        && !ingress.get().getSpec().getTls().isEmpty();
                return new NetworkingInfo(true, host, tls);
            }
        } catch (KubernetesClientException e) {
            warnings.add(mapClientException("ingresses", e));
        } catch (RuntimeException e) {
            warnings.add(CollectionWarning.collectionFailed("ingresses", e.getMessage()));
        }

        return new NetworkingInfo(false, null, false);
    }

    private static CollectionWarning mapClientException(String resource, KubernetesClientException e) {
        if (e.getCode() == 403) {
            return CollectionWarning.permissionDenied(resource, e.getMessage());
        }
        if (e.getCode() == 404) {
            return new CollectionWarning(
                    CollectionWarning.WarningCode.RESOURCE_NOT_FOUND, resource, e.getMessage());
        }
        return CollectionWarning.apiUnavailable(resource, e.getMessage());
    }

    private static String label(Map<String, String> labels, String key) {
        if (labels == null) {
            return null;
        }
        return labels.get(key);
    }

    private record WorkloadRef(
            DeploymentMethod method,
            String name,
            int desired,
            int ready,
            int current,
            int available,
            PodSpec podSpec,
            Map<String, String> selector,
            GenericKubernetesResource cr) {

        static WorkloadRef none() {
            return new WorkloadRef(DeploymentMethod.UNKNOWN, null, -1, -1, -1, -1, null, Map.of(), null);
        }
    }
}
