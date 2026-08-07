package io.github.keycloakmcp.adapter.infrastructure;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.github.keycloakmcp.target.InfrastructureType;

/**
 * Fabric8-backed implementation of {@link ClusterClient}.
 * <p>
 * The OpenShift client is obtained via {@code KubernetesClient.adapt(OpenShiftClient.class)};
 * it shares the underlying connection pool with the base client.
 * Type detection is performed lazily on first call to {@link #type()}.
 */
class DefaultClusterClient implements ClusterClient {

    private static final Logger LOG = Logger.getLogger(DefaultClusterClient.class);
    private static final String ROUTE_API_GROUP = "route.openshift.io";
    private static final String CONFIG_API_GROUP = "config.openshift.io";

    private final KubernetesClient kubernetesClient;
    private final String namespace;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private volatile InfrastructureType detectedType;
    private volatile OpenShiftClient openShiftClient;

    DefaultClusterClient(KubernetesClient kubernetesClient, String namespace) {
        this.kubernetesClient = kubernetesClient;
        this.namespace = namespace;
    }

    @Override
    public KubernetesClient kubernetes() {
        return kubernetesClient;
    }

    @Override
    public Optional<OpenShiftClient> openshift() {
        if (type() != InfrastructureType.OPENSHIFT) {
            return Optional.empty();
        }
        if (openShiftClient == null) {
            synchronized (this) {
                if (openShiftClient == null) {
                    openShiftClient = kubernetesClient.adapt(OpenShiftClient.class);
                }
            }
        }
        return Optional.ofNullable(openShiftClient);
    }

    @Override
    public String namespace() {
        return namespace;
    }

    @Override
    public InfrastructureType type() {
        if (detectedType == null) {
            synchronized (this) {
                if (detectedType == null) {
                    detectedType = detectType();
                }
            }
        }
        return detectedType;
    }

    /** Allow callers to provide the type hint to avoid an extra API round-trip. */
    void setTypeHint(InfrastructureType hint) {
        this.detectedType = hint;
    }

    private InfrastructureType detectType() {
        try {
            Set<String> groups = kubernetesClient.getApiGroups().getGroups().stream()
                    .map(g -> g.getName())
                    .collect(Collectors.toSet());
            if (groups.contains(ROUTE_API_GROUP) || groups.contains(CONFIG_API_GROUP)) {
                return InfrastructureType.OPENSHIFT;
            }
        } catch (RuntimeException e) {
            LOG.debugf(e, "Unable to probe API groups for type detection; defaulting to KUBERNETES");
        }
        return InfrastructureType.KUBERNETES;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            // openShiftClient shares the underlying connection; only close the base client
            try {
                kubernetesClient.close();
            } catch (RuntimeException e) {
                LOG.debugf(e, "Error closing Kubernetes client");
            }
        }
    }
}
