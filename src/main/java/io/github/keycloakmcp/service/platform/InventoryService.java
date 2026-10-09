package io.github.keycloakmcp.service.platform;

import static io.github.keycloakmcp.adapter.infrastructure.BoundedKubernetesReader.*;


import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.github.keycloakmcp.target.KubernetesInstallationBinding;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.adapter.infrastructure.InfrastructureClientFactory;
import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.discovery.EnvironmentDiscovery;
import io.github.keycloakmcp.discovery.EnvironmentInfo;
import io.github.keycloakmcp.discovery.ClusterApiCapabilities.ApiAvailability;
import io.github.keycloakmcp.discovery.DetectionConfidence;
import io.github.keycloakmcp.discovery.RuntimeType;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.inventory.ClusterInfo;
import io.github.keycloakmcp.domain.inventory.CollectionWarning;
import io.github.keycloakmcp.domain.inventory.DeploymentMethod;
import io.github.keycloakmcp.domain.inventory.HpaInfo;
import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
import io.github.keycloakmcp.domain.inventory.InfrastructureCoverage;
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

    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final InfrastructureClientFactory clientFactory;
    private final EnvironmentDiscovery environmentDiscovery;

    @ConfigProperty(name = "collection.operation-timeout-ms", defaultValue = "30000")
    long collectionTimeoutMs = CollectionBudget.DEFAULT_TIMEOUT_MS;

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

    /** Internal bounded configuration association; raw Services are not an API/report payload. */
    public record ServiceAssociation(String namespace, List<Service> services, List<Service> namespaceServices, boolean complete) {
        public ServiceAssociation {
            services = List.copyOf(services);
            namespaceServices = List.copyOf(namespaceServices);
        }

        public ServiceAssociation(String namespace, List<Service> services, boolean complete) {
            this(namespace, services, services, complete);
        }
    }

    /** Revalidates a passed target and its exact installation without collecting nodes or exposure resources. */
    public ServiceAssociation associatedServices(Target target, ClusterClient cluster) {
        if (target == null) return new ServiceAssociation(null, List.of(), List.of(), false);
        Target current = targetResolver.require(target.id().value());
        targetAuthorization.assertAllowed(current, TargetPermission.READ);
        String namespace = target.infrastructure() == null ? null : target.infrastructure().namespace();
        if (!current.equals(target) || cluster == null || !target.hasInfrastructure()
                || (target.infrastructureTypeOrNone() != InfrastructureType.KUBERNETES
                    && target.infrastructureTypeOrNone() != InfrastructureType.OPENSHIFT)
                || target.infrastructure().installation() == null || namespace == null || namespace.isBlank()
                || !namespace.equals(cluster.namespace()) || Thread.currentThread().isInterrupted()) {
            return new ServiceAssociation(namespace, List.of(), List.of(), false);
        }
        var warnings = new ArrayList<CollectionWarning>();
        try (var scope = CollectionBudget.open(target.id().value(), collectionTimeoutMs)) {
            InstallationServiceCollector.checkpoint();
            WorkloadRef workload = findWorkload(cluster.kubernetes(), namespace, target.infrastructure().installation(), warnings);
            if (!warnings.isEmpty() || workload.method() == DeploymentMethod.UNKNOWN || workload.podSpec() == null) {
                return new ServiceAssociation(namespace, List.of(), List.of(), false);
            }
            var selection = new InstallationServiceCollector().collect(cluster, toWorkloadInfo(workload, namespace), warnings);
            InstallationServiceCollector.checkpoint();
            return new ServiceAssociation(namespace, List.copyOf(selection.services().values()), selection.namespaceServices(),
                    selection.associationComplete());
        } catch (RuntimeException e) {
            // Never expose raw resources, endpoint details or exception messages through this association.
            return new ServiceAssociation(namespace, List.of(), List.of(), false);
        }
    }

    public InfrastructureInventory collect(String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);

        if (!target.hasInfrastructure()
                || target.infrastructureTypeOrNone() == InfrastructureType.NONE
                || target.infrastructureTypeOrNone() == InfrastructureType.VM) {
            return unavailable(targetId, new CollectionWarning(
                    target.infrastructureTypeOrNone() == InfrastructureType.VM
                            ? CollectionWarning.WarningCode.NOT_SUPPORTED : CollectionWarning.WarningCode.NOT_CONFIGURED,
                    "infrastructure", null), environmentDiscovery.discover(target));
        }

        if (target.infrastructure().installation() == null) {
            return unavailable(targetId, new CollectionWarning(CollectionWarning.WarningCode.BINDING_REQUIRED,
                    "installation", "Confirm an explicit installation binding before collecting infrastructure"));
        }
        try (var scope = CollectionBudget.open(targetId, collectionTimeoutMs)) {
            try {
                scope.budget().checkpoint();
                return withBudgetWarning(collectWithinBudget(target), scope.budget());
            } catch (CollectionBudget.Aborted e) {
                return unavailable(targetId, budgetWarning(e.reason()));
            }
        }
    }

    private InfrastructureInventory collectWithinBudget(Target target) {
        String targetId = target.id().value();
        CollectionBudget.checkpoint(targetId);
        Optional<ClusterClient> clientOpt = clientFactory.resolve(target);
        CollectionBudget.checkpoint(targetId);
        if (clientOpt.isEmpty()) {
            throw McpException.unsupportedCapability(
                    "Infrastructure client unavailable for target '" + targetId + "'");
        }

        ClusterClient clusterClient = clientOpt.get();
        String namespace = target.infrastructure().namespace();
        if (!present(namespace) || !namespace.equals(clusterClient.namespace())) {
            return unavailable(targetId, CollectionWarning.collectionFailed("infrastructure", null));
        }
        KubernetesClient k8s = clusterClient.kubernetes();
        List<CollectionWarning> warnings = new ArrayList<>();

        EnvironmentInfo env = environmentDiscovery.discover(target);
        if (env == null || !targetId.equals(env.targetId())
                || (env.namespace() != null && !namespace.equals(env.namespace()))
                || (env.confidence() == DetectionConfidence.CONFIRMED && !namespace.equals(env.namespace()))) {
            return unavailable(targetId, CollectionWarning.collectionFailed("discovery", null));
        }
        String runtime = env.runtime() == null ? "UNKNOWN" : env.runtime().name();
        if (env.runtime() == null || env.runtime() == RuntimeType.UNKNOWN
                || env.apiCapabilities().routeV1() == ApiAvailability.UNKNOWN
                || env.apiCapabilities().configV1() == ApiAvailability.UNKNOWN) {
            warnings.add(CollectionWarning.collectionFailed("discovery", null));
        }
        if (!present(env.clusterVersion())) {
            warnings.add(CollectionWarning.collectionFailed("cluster-api-version", null));
        }

        ClusterObservation clusterObservation = collectCluster(k8s, env, warnings);
        ClusterInfo cluster = clusterObservation.info();
        WorkloadRef workload = findWorkload(k8s, namespace, target.infrastructure().installation(), warnings);
        if (workload.method() == DeploymentMethod.UNKNOWN) {
            if (warnings.isEmpty()) warnings.add(CollectionWarning.collectionFailed("installation", null));
            return new InfrastructureInventory(targetId, runtime, cluster, KeycloakWorkloadInfo.unknown(namespace),
                    List.of(), null, null, null, null, null, null, null, List.copyOf(warnings), Instant.now(), env);
        }
        KeycloakWorkloadInfo keycloak = toWorkloadInfo(workload, namespace);
        if (workload.podSpec() == null && warnings.stream().noneMatch(w -> "workload".equals(w.resource()))) {
            warnings.add(CollectionWarning.collectionFailed("workload", "Bound workload template unavailable"));
        }
        List<PodInventoryItem> pods;
        if (collectionActive()) pods = collectPods(k8s, namespace, workload, clusterObservation.nodes(), warnings);
        else {
            pods = List.of();
            warnings.add(CollectionWarning.collectionFailed("pods", null));
        }
        TopologyInfo topology = buildTopology(pods);
        SchedulingInfo scheduling = workload.podSpec() == null || !collectionActive() ? null : collectScheduling(workload);
        HpaInfo hpa = workload.podSpec() == null || !collectionActive() ? null : collectHpa(k8s, namespace, workload, warnings);
        PdbInfo pdb = workload.podSpec() == null || !collectionActive() ? null : collectPdb(k8s, namespace, workload, warnings);
        ResourceConfig resources = workload.podSpec() == null || !collectionActive() ? null : collectResources(workload);
        ProbeInfo probes = workload.podSpec() == null || !collectionActive() ? null : collectProbes(workload);
        NetworkingInfo networking = workload.podSpec() == null || !collectionActive() ? null
                : new InstallationNetworkingCollector().collect(clusterClient, keycloak, env.apiCapabilities(), warnings);

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
                Instant.now(), env);
    }

    private static boolean collectionActive() {
        CollectionBudget budget = CollectionBudget.current();
        return budget == null ? !Thread.currentThread().isInterrupted() : !budget.exhausted();
    }

    private static CollectionWarning budgetWarning(String reason) {
        return new CollectionWarning(CollectionBudget.REASON_INTERRUPTED.equals(reason)
                ? CollectionWarning.WarningCode.OPERATION_INTERRUPTED : CollectionWarning.WarningCode.OPERATION_BUDGET_EXCEEDED,
                "collection-budget", null);
    }

    private static InfrastructureInventory withBudgetWarning(InfrastructureInventory observed, CollectionBudget budget) {
        if (!budget.exhausted()) return observed;
        var warnings = new ArrayList<>(observed.warnings() == null ? List.of() : observed.warnings());
        var warning = budgetWarning(budget.reason());
        if (!warnings.contains(warning)) warnings.add(warning);
        return new InfrastructureInventory(observed.targetId(), observed.runtime(), observed.cluster(), observed.keycloak(),
                observed.pods(), observed.topology(), observed.scheduling(), observed.hpa(), observed.pdb(), observed.resources(),
                observed.probes(), observed.networking(), List.copyOf(warnings), observed.collectedAt(), observed.discovery());
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
            add(out, targetId, "workload", "keycloak.workload.uid", kc.uid(), now);
            add(out, targetId, "workload", "keycloak.workload.kind", kc.kind(), now);
            add(out, targetId, "workload", "keycloak.workload.apiVersion", kc.apiVersion(), now);
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
        List<PodInventoryItem> pods = inventory.pods();
        boolean podsKnown = pods != null && pods.stream().noneMatch(java.util.Objects::isNull);
        if (podsKnown) {
            add(out, targetId, "pods", "keycloak.pods.total", pods.size(), now);
            long ready = pods.stream().filter(PodInventoryItem::ready).count();
            add(out, targetId, "pods", "keycloak.pods.ready", ready, now);
            if (pods.stream().allMatch(p -> p.restartCount() >= 0)) {
                long restarts = pods.stream().mapToLong(PodInventoryItem::restartCount).sum();
                add(out, targetId, "pods", "keycloak.pods.restartCount", restarts, now);
            }
            long oom = pods.stream().filter(PodInventoryItem::oomKilled).count();
            add(out, targetId, "pods", "keycloak.pods.oomKilledCount", oom, now);
        }

        TopologyInfo topo = inventory.topology();
        boolean zonesKnown = podsKnown && topo != null && topologyMatches(pods, topo.podsByZone(), true)
                && topo.zoneCount() == topo.podsByZone().size();
        boolean nodesKnown = podsKnown && topo != null && topologyMatches(pods, topo.podsByNode(), false);
        if (zonesKnown) {
            add(out, targetId, "topology", "keycloak.topology.zoneCount", topo.zoneCount(), now);
            add(out, targetId, "topology", "keycloak.topology.podsByZone", topo.podsByZone(), now);
            if (c != null && c.zoneCount() >= 0) {
                add(out, targetId, "topology", "keycloak.topology.singleZoneConcentration",
                        isSingleBucketConcentration(topo.podsByZone(), c.zoneCount()), now);
            }
        }
        if (nodesKnown) {
            add(out, targetId, "topology", "keycloak.topology.podsByNode", topo.podsByNode(), now);
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
                if (hpa.minReplicas() >= 0) add(out, targetId, "autoscaling", "keycloak.hpa.minReplicas", hpa.minReplicas(), now);
                if (hpa.maxReplicas() >= 0) add(out, targetId, "autoscaling", "keycloak.hpa.maxReplicas", hpa.maxReplicas(), now);
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
        if (net != null && net.complete()) {
            add(out, targetId, "networking", "keycloak.route.present", net.routeOrIngressPresent(), now);
        }
        if (inventory.warnings() != null) {
            for (CollectionWarning w : inventory.warnings()) {
                String resource = w == null || w.resource() == null ? "unknown" : w.resource();
                add(out, targetId, "collection", "collection.warning." + resource,
                        w == null ? "COLLECTION_FAILED" : w.code().name(), now);
                // An unavailable collector is not evidence of an absent resource or a healthy zero.
                java.util.Set<String> unavailableCategories = switch (resource) {
                    case "unknown" -> java.util.Set.of("cluster", "workload", "pods", "topology", "scheduling", "autoscaling", "disruption", "resources", "probes", "networking");
                    case "installation" -> java.util.Set.of("workload", "pods", "topology", "scheduling", "autoscaling", "disruption", "resources", "probes", "networking");
                    case "workload" -> java.util.Set.of("pods", "topology", "scheduling", "autoscaling", "disruption", "resources", "probes", "networking");
                    case "pods" -> java.util.Set.of("pods", "topology");
                    case "hpa" -> java.util.Set.of("autoscaling");
                    case "pdb" -> java.util.Set.of("disruption");
                    case "networking" -> java.util.Set.of("networking");
                    case "resources" -> java.util.Set.of("resources");
                    case "probes" -> java.util.Set.of("probes");
                    default -> java.util.Set.of();
                };
                out.removeIf(e -> unavailableCategories.contains(e.category()));
                if ("nodes".equals(resource)) {
                    out.removeIf(e -> java.util.Set.of("cluster.nodes.count", "cluster.zones.count",
                            "keycloak.topology.singleZoneConcentration").contains(e.key()));
                }
                if ("node-zones".equals(resource)) {
                    out.removeIf(e -> java.util.Set.of("cluster.zones.count", "keycloak.topology.singleZoneConcentration").contains(e.key()));
                }
                if ("pod-zones".equals(resource)) {
                    out.removeIf(e -> java.util.Set.of("keycloak.topology.zoneCount", "keycloak.topology.podsByZone",
                            "keycloak.topology.singleZoneConcentration").contains(e.key()));
                }
                if ("infrastructure".equals(resource) || "openshift-config".equals(resource)) {
                    out.removeIf(e -> "cluster.platform".equals(e.key()));
                }
                if ("clusterversion".equals(resource) || "openshift-config".equals(resource)) {
                    out.removeIf(e -> "cluster.version".equals(e.key()));
                }
                if ("infrastructure".equals(resource) && w.code() == CollectionWarning.WarningCode.NOT_CONFIGURED) {
                    out.removeIf(e -> !java.util.Set.of("runtime", "collection").contains(e.category()));
                }
            }
        }
        boolean workloadKnown = kc != null && kc.deploymentMethod() != null
                && kc.deploymentMethod() != DeploymentMethod.UNKNOWN && present(kc.name()) && present(kc.namespace());
        if (!workloadKnown) {
            out.removeIf(e -> !java.util.Set.of("runtime", "cluster", "collection").contains(e.category()));
        }
        boolean complete = InfrastructureCoverage.isComplete(inventory);
        add(out, targetId, "collection", "infrastructure.collection.complete", complete, now);
        return List.copyOf(out);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean topologyMatches(List<PodInventoryItem> pods, Map<String, Integer> buckets, boolean zones) {
        if (buckets == null) return false;
        Map<String, Integer> observed = new LinkedHashMap<>();
        for (PodInventoryItem pod : pods) {
            String bucket = zones ? pod.zone() : pod.nodeName();
            if (!present(bucket)) return false;
            observed.merge(bucket, 1, Integer::sum);
        }
        return observed.equals(buckets);
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
        long total = buckets.values().stream().mapToLong(Integer::longValue).sum();
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

    private record ClusterObservation(ClusterInfo info, List<Node> nodes) { }

    private ClusterObservation collectCluster(
            KubernetesClient k8s,
            EnvironmentInfo env,
            List<CollectionWarning> warnings) {
        String distribution = env.runtime() == RuntimeType.OPENSHIFT ? "openshift"
                : env.runtime() == RuntimeType.KUBERNETES ? "kubernetes" : null;
        String version = env.clusterVersion();
        String platform = null;
        int nodeCount = -1;
        int zoneCount = -1;
        List<Node> observedNodes = null;

        try {
            List<Node> nodes = list(k8s, NODES, null);
            observedNodes = nodes;
            nodeCount = nodes.size();
            Map<String, Integer> zones = new LinkedHashMap<>();
            for (Node node : nodes) {
                CollectionBudget.checkpointCurrent();
                String zone = label(node.getMetadata() == null ? null : node.getMetadata().getLabels(), ZONE_LABEL);
                if (present(zone)) {
                    zones.merge(zone, 1, Integer::sum);
                }
            }
            boolean missingZones = nodes.stream().anyMatch(node -> !present(label(
                    node.getMetadata() == null ? null : node.getMetadata().getLabels(), ZONE_LABEL)));
            zoneCount = missingZones ? -1 : zones.size();
            if (missingZones) warnings.add(CollectionWarning.collectionFailed("node-zones", null));
            CollectionBudget.checkpointCurrent();
        } catch (KubernetesClientException e) {
            warnings.add(mapClientException("nodes", e));
        } catch (RuntimeException e) {
            warnings.add(CollectionWarning.collectionFailed("nodes", e.getMessage()));
        }

        if (env.apiCapabilities().configV1() == ApiAvailability.SERVED && collectionActive()) {
            platform = readOpenShiftPlatform(k8s, warnings);
            String ocpVersion = collectionActive() ? readOpenShiftVersion(k8s, warnings) : null;
            if (ocpVersion != null) {
                version = ocpVersion;
            }
        } else if (env.runtime() == RuntimeType.OPENSHIFT
                || env.apiCapabilities().configV1() == ApiAvailability.UNKNOWN) {
            warnings.add(new CollectionWarning(env.apiCapabilities().configV1() == ApiAvailability.UNKNOWN
                    ? CollectionWarning.WarningCode.COLLECTION_FAILED : CollectionWarning.WarningCode.NOT_SUPPORTED,
                    "openshift-config", null));
        }

        return new ClusterObservation(new ClusterInfo(distribution, version, platform, nodeCount, zoneCount), observedNodes);
    }

    private String readOpenShiftPlatform(KubernetesClient oc, List<CollectionWarning> warnings) {
        try {
            var infra = get(oc, INFRASTRUCTURES, null, "cluster");
            if (infra != null && infra.getStatus() != null && infra.getStatus().getPlatform() != null) {
                return infra.getStatus().getPlatform();
            }
            warnings.add(CollectionWarning.collectionFailed("infrastructure", null));
        } catch (KubernetesClientException e) {
            warnings.add(mapClientException("infrastructure", e));
        } catch (RuntimeException e) {
            warnings.add(CollectionWarning.collectionFailed("infrastructure", e.getMessage()));
        }
        return null;
    }

    private String readOpenShiftVersion(KubernetesClient oc, List<CollectionWarning> warnings) {
        try {
            var cv = get(oc, CLUSTER_VERSIONS, null, "version");
            if (cv != null && cv.getStatus() != null && cv.getStatus().getDesired() != null
                    && present(cv.getStatus().getDesired().getVersion())) {
                return cv.getStatus().getDesired().getVersion();
            }
            warnings.add(CollectionWarning.collectionFailed("clusterversion", null));
        } catch (KubernetesClientException e) {
            warnings.add(mapClientException("clusterversion", e));
        } catch (RuntimeException e) {
            warnings.add(CollectionWarning.collectionFailed("clusterversion", e.getMessage()));
        }
        return null;
    }

    private WorkloadRef findWorkload(KubernetesClient k8s, String namespace,
            KubernetesInstallationBinding binding, List<CollectionWarning> warnings) {
        try {
            InstallationServiceCollector.checkpoint();
            HasMetadata resource;
            if ("Deployment".equals(binding.kind())) {
                resource = get(k8s, DEPLOYMENTS, namespace, binding.name());
            } else if ("StatefulSet".equals(binding.kind())) {
                resource = get(k8s, STATEFUL_SETS, namespace, binding.name());
            } else {
                resource = get(k8s, keycloaks(binding.apiVersion()), namespace, binding.name());
            }
            if (resource == null) {
                warnings.add(new CollectionWarning(CollectionWarning.WarningCode.RESOURCE_NOT_FOUND, "installation",
                        "The bound installation was not found; no alternative was selected"));
                return WorkloadRef.none();
            }
            if (resource.getMetadata() == null || !binding.uid().equals(resource.getMetadata().getUid())
                    || !binding.name().equals(resource.getMetadata().getName())
                    || !namespace.equals(resource.getMetadata().getNamespace())
                    || !binding.kind().equals(resource.getKind())
                    || !binding.apiVersion().equals(resource.getApiVersion())) {
                warnings.add(new CollectionWarning(CollectionWarning.WarningCode.BINDING_MISMATCH, "installation",
                        "Installation identity changed; explicit reconfirmation is required"));
                return WorkloadRef.none();
            }
            if (resource instanceof Deployment deployment) return fromDeployment(deployment, DeploymentMethod.DEPLOYMENT);
            if (resource instanceof StatefulSet statefulSet) return fromStatefulSet(statefulSet, DeploymentMethod.STATEFULSET);

            // An Operator CR is not proof that its desired instances are running.
            var cr = (GenericKubernetesResource) resource;
            List<WorkloadRef> children = new ArrayList<>();
            List<Deployment> deployments = list(k8s, DEPLOYMENTS, namespace);
            for (Deployment d : deployments) {
                CollectionBudget.checkpointCurrent();
                if (ownedBy(d, binding.uid(), "Keycloak", binding.apiVersion())) children.add(fromDeployment(d, DeploymentMethod.KEYCLOAK_OPERATOR));
            }
            List<StatefulSet> statefulSets = list(k8s, STATEFUL_SETS, namespace);
            for (StatefulSet sts : statefulSets) {
                CollectionBudget.checkpointCurrent();
                if (ownedBy(sts, binding.uid(), "Keycloak", binding.apiVersion())) children.add(fromStatefulSet(sts, DeploymentMethod.KEYCLOAK_OPERATOR));
            }
            if (children.size() > 1) {
                warnings.add(new CollectionWarning(CollectionWarning.WarningCode.AMBIGUOUS_RESOURCE, "installation",
                        "Multiple controller-owned workloads require explicit reconciliation"));
                return WorkloadRef.none();
            }
            if (children.size() == 1) return children.getFirst();
            warnings.add(new CollectionWarning(CollectionWarning.WarningCode.RESOURCE_NOT_FOUND, "workload",
                    "Bound Operator CR has no verified controller-owned workload"));
            return new WorkloadRef(DeploymentMethod.KEYCLOAK_OPERATOR, binding.name(), readCrInstances(cr),
                    -1, -1, -1, null, Map.of(), cr, binding.uid(), binding.kind(), binding.apiVersion());
        } catch (KubernetesClientException e) {
            warnings.add(mapClientException("installation", e));
        } catch (RuntimeException e) {
            warnings.add(CollectionWarning.collectionFailed("installation", "Bound installation could not be read"));
        }
        return WorkloadRef.none();
    }

    private static WorkloadRef fromDeployment(Deployment d, DeploymentMethod method) {
        var spec = d.getSpec();
        var status = d.getStatus();
        var template = spec == null ? null : spec.getTemplate();
        return new WorkloadRef(method, d.getMetadata().getName(),
                spec == null || spec.getReplicas() == null ? -1 : spec.getReplicas(),
                status == null || status.getReadyReplicas() == null ? -1 : status.getReadyReplicas(),
                status == null || status.getReplicas() == null ? -1 : status.getReplicas(),
                status == null || status.getAvailableReplicas() == null ? -1 : status.getAvailableReplicas(),
                template == null ? null : template.getSpec(),
                template == null || template.getMetadata() == null || template.getMetadata().getLabels() == null ? Map.of() : template.getMetadata().getLabels(),
                null, d.getMetadata().getUid(), d.getKind(), d.getApiVersion());
    }

    private static WorkloadRef fromStatefulSet(StatefulSet sts, DeploymentMethod method) {
        var spec = sts.getSpec();
        var status = sts.getStatus();
        var template = spec == null ? null : spec.getTemplate();
        return new WorkloadRef(method, sts.getMetadata().getName(),
                spec == null || spec.getReplicas() == null ? -1 : spec.getReplicas(),
                status == null || status.getReadyReplicas() == null ? -1 : status.getReadyReplicas(),
                status == null || status.getReplicas() == null ? -1 : status.getReplicas(),
                status == null || status.getAvailableReplicas() == null ? -1 : status.getAvailableReplicas(),
                template == null ? null : template.getSpec(),
                template == null || template.getMetadata() == null || template.getMetadata().getLabels() == null ? Map.of() : template.getMetadata().getLabels(),
                null, sts.getMetadata().getUid(), sts.getKind(), sts.getApiVersion());
    }

    private static boolean ownedBy(HasMetadata resource, String uid, String kind, String apiVersion) {
        return uid != null && resource.getMetadata() != null && resource.getMetadata().getOwnerReferences() != null
                && resource.getMetadata().getOwnerReferences().stream().anyMatch(owner ->
                        uid.equals(owner.getUid()) && kind.equals(owner.getKind()) && apiVersion.equals(owner.getApiVersion())
                                && Boolean.TRUE.equals(owner.getController()));
    }

    private static InfrastructureInventory unavailable(String targetId, CollectionWarning warning) {
        return unavailable(targetId, warning, null);
    }

    private static InfrastructureInventory unavailable(String targetId, CollectionWarning warning, EnvironmentInfo discovery) {
        boolean collectionAborted = "collection-budget".equals(warning.resource());
        return new InfrastructureInventory(targetId, "UNKNOWN", new ClusterInfo(null, null, null, -1, -1),
                KeycloakWorkloadInfo.unknown(null), collectionAborted ? null : List.of(),
                collectionAborted ? null : new TopologyInfo(Map.of(), Map.of(), 0),
                null, null, null, null, null, null, List.of(warning), Instant.now(), discovery);
    }

    static int readCrInstances(GenericKubernetesResource cr) {
        Object spec = cr.get("spec");
        if (spec instanceof Map<?, ?> map) {
            Object instances = map.get("instances");
            if (instances instanceof Number number) {
                try {
                    int value = new java.math.BigDecimal(number.toString()).intValueExact();
                    return value >= 0 ? value : -1;
                } catch (NumberFormatException | ArithmeticException ignored) {
                    return -1;
                }
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
                workload.available(), workload.apiVersion(), workload.kind(), workload.uid());
    }

    private List<PodInventoryItem> collectPods(
            KubernetesClient k8s,
            String namespace,
            WorkloadRef workload,
            List<Node> observedNodes,
            List<CollectionWarning> warnings) {
        if (namespace == null || namespace.isBlank()) {
            return List.of();
        }
        try {
            if (workload.uid() == null || workload.podSpec() == null) return List.of();
            var replicaSetUids = new java.util.HashSet<String>();
            if ("Deployment".equals(workload.kind())) {
                for (var rs : list(k8s, REPLICA_SETS, namespace)) {
                    CollectionBudget.checkpointCurrent();
                    if (ownedBy(rs, workload.uid(), "Deployment", workload.apiVersion())) replicaSetUids.add(rs.getMetadata().getUid());
                }
            }
            List<Pod> pods = list(k8s, PODS, namespace).stream()
                    .filter(p -> namespace.equals(p.getMetadata().getNamespace()))
                    .filter(p -> ownedBy(p, workload.uid(), workload.kind(), workload.apiVersion())
                            || replicaSetUids.stream().anyMatch(uid -> ownedBy(p, uid, "ReplicaSet", "apps/v1")))
                    .toList();

            Map<String, String> nodeZones = new LinkedHashMap<>();
            if (observedNodes != null) {
                for (Node node : observedNodes) {
                    CollectionBudget.checkpointCurrent();
                    String nodeName = node.getMetadata() != null ? node.getMetadata().getName() : null;
                    if (nodeName != null) {
                        nodeZones.put(nodeName,
                                label(node.getMetadata().getLabels(), ZONE_LABEL));
                    }
                }
            } else {
                LOG.debug("Unable to map node zones while collecting pods");
                warnings.add(CollectionWarning.collectionFailed("pod-zones", "Node topology unavailable"));
            }

            List<PodInventoryItem> items = new ArrayList<>();
            for (Pod pod : pods) {
                CollectionBudget.checkpointCurrent();
                String name = pod.getMetadata() != null ? pod.getMetadata().getName() : null;
                String nodeName = pod.getSpec() != null ? pod.getSpec().getNodeName() : null;
                String zone = nodeName == null ? null : nodeZones.get(nodeName);
                boolean ready = isPodReady(pod);
                int restarts = restartCount(pod);
                boolean oom = isOomKilled(pod);
                if (!podStatusKnown(pod)) {
                    var warning = CollectionWarning.collectionFailed("pods", null);
                    if (!warnings.contains(warning)) warnings.add(warning);
                }
                items.add(new PodInventoryItem(name, nodeName, zone, ready, restarts, oom));
            }
            CollectionBudget.checkpointCurrent();
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
        if (!podStatusKnown(pod)) {
            return false;
        }
        return pod.getStatus().getConditions().stream()
                .anyMatch(c -> c != null && "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
    }

    private static int restartCount(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null
                || pod.getStatus().getContainerStatuses().isEmpty()) {
            return -1;
        }
        long total = 0;
        for (ContainerStatus status : pod.getStatus().getContainerStatuses()) {
            if (status == null || status.getRestartCount() == null || status.getRestartCount() < 0) return -1;
            total += status.getRestartCount();
        }
        return total <= Integer.MAX_VALUE ? (int) total : -1;
    }

    private static boolean podStatusKnown(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getConditions() == null || restartCount(pod) < 0) return false;
        var conditions = pod.getStatus().getConditions();
        return conditions.stream().noneMatch(java.util.Objects::isNull)
                && conditions.stream().filter(c -> "Ready".equals(c.getType())).count() == 1
                && conditions.stream().anyMatch(c -> "Ready".equals(c.getType())
                        && ("True".equals(c.getStatus()) || "False".equals(c.getStatus())));
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
                    list(k8s, HPAS, namespace);
            HpaInfo selected = null;
            for (HorizontalPodAutoscaler hpa : hpas) {
                CollectionBudget.checkpointCurrent();
                if (hpa.getSpec() == null || hpa.getSpec().getScaleTargetRef() == null) {
                    throw new IllegalStateException("Incomplete HPA configuration");
                }
                String refName = hpa.getSpec().getScaleTargetRef().getName();
                if (!present(refName) || !present(hpa.getSpec().getScaleTargetRef().getKind())
                        || !present(hpa.getSpec().getScaleTargetRef().getApiVersion())) {
                    throw new IllegalStateException("Incomplete HPA association");
                }
                if (workload.name().equals(refName)
                        && workload.kind().equals(hpa.getSpec().getScaleTargetRef().getKind())
                        && workload.apiVersion().equals(hpa.getSpec().getScaleTargetRef().getApiVersion())) {
                    int min = hpa.getSpec().getMinReplicas() == null ? -1 : hpa.getSpec().getMinReplicas();
                    int max = hpa.getSpec().getMaxReplicas() == null ? -1 : hpa.getSpec().getMaxReplicas();
                    int current = hpa.getStatus() != null && hpa.getStatus().getCurrentReplicas() != null
                            ? hpa.getStatus().getCurrentReplicas() : -1;
                    if (selected != null) {
                        warnings.add(new CollectionWarning(CollectionWarning.WarningCode.AMBIGUOUS_RESOURCE, "hpa", "Multiple HPAs target the bound workload"));
                        return null;
                    }
                    selected = new HpaInfo(true, min, max, current);
                }
            }
            CollectionBudget.checkpointCurrent();
            return selected == null ? HpaInfo.absent() : selected;
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
                    list(k8s, PDBS, namespace);
            PdbInfo selected = null;
            for (PodDisruptionBudget pdb : pdbs) {
                CollectionBudget.checkpointCurrent();
                if (pdb.getSpec() == null) {
                    throw new IllegalStateException("Incomplete PDB configuration");
                }
                var selector = pdb.getSpec().getSelector();
                if (selector == null || workload.selector() == null) continue;
                if (selector.getMatchExpressions() != null && !selector.getMatchExpressions().isEmpty()) {
                    warnings.add(new CollectionWarning(CollectionWarning.WarningCode.NOT_SUPPORTED, "pdb",
                            "Expression-based PDB association is not yet evaluated"));
                    continue;
                }
                Map<String, String> labels = selector.getMatchLabels();
                if (labels != null && !labels.entrySet().stream().allMatch(e -> e.getValue().equals(workload.selector().get(e.getKey())))) continue;
                String minAvailable = pdb.getSpec().getMinAvailable() == null
                        ? null : pdb.getSpec().getMinAvailable().toString();
                String maxUnavailable = pdb.getSpec().getMaxUnavailable() == null
                        ? null : pdb.getSpec().getMaxUnavailable().toString();
                if (selected != null) {
                    warnings.add(new CollectionWarning(CollectionWarning.WarningCode.AMBIGUOUS_RESOURCE, "pdb", "Multiple PDBs apply; combined policy is not yet evaluated"));
                    return null;
                }
                selected = new PdbInfo(true, minAvailable, maxUnavailable);
            }
            if (warnings.stream().anyMatch(w -> "pdb".equals(w.resource()))) return null;
            CollectionBudget.checkpointCurrent();
            return selected == null ? PdbInfo.absent() : selected;
        } catch (KubernetesClientException e) {
            warnings.add(mapClientException("pdb", e));
            return PdbInfo.absent();
        } catch (RuntimeException e) {
            warnings.add(CollectionWarning.collectionFailed("pdb", e.getMessage()));
            return PdbInfo.absent();
        }
    }

    private ResourceConfig collectResources(WorkloadRef workload) {
        Container container = primaryContainer(workload.podSpec());
        if (container == null) {
            return null;
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
            return null;
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

    private static CollectionWarning mapClientException(String resource, KubernetesClientException e) {
        if (e.getCode() == 401 || e.getCode() == 403) {
            return CollectionWarning.permissionDenied(resource, null);
        }
        if (e.getCode() == 404) {
            return new CollectionWarning(
                    CollectionWarning.WarningCode.RESOURCE_NOT_FOUND, resource, null);
        }
        return CollectionWarning.apiUnavailable(resource, null);
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
            GenericKubernetesResource cr,
            String uid,
            String kind,
            String apiVersion) {

        static WorkloadRef none() {
            return new WorkloadRef(DeploymentMethod.UNKNOWN, null, -1, -1, -1, -1, null, Map.of(), null, null, null, null);
        }
    }
}
