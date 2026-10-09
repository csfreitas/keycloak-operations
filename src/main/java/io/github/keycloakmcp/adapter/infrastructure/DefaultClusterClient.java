package io.github.keycloakmcp.adapter.infrastructure;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.github.keycloakmcp.target.InfrastructureType;

/**
 * Fabric8-backed implementation of {@link ClusterClient}.
 * <p>
 * The OpenShift client is obtained via {@code KubernetesClient.adapt(OpenShiftClient.class)};
 * it shares the underlying connection pool with the base client.
 * Type is a configured hint only; this wrapper performs no implicit discovery.
 */
class DefaultClusterClient implements ClusterClient {

    private static final Logger LOG = Logger.getLogger(DefaultClusterClient.class);

    private final KubernetesClient kubernetesClient;
    private final String namespace;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private volatile InfrastructureType configuredType = InfrastructureType.NONE;
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
    @Deprecated
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
    @Deprecated
    public InfrastructureType type() {
        return configuredType;
    }

    /** Retain operator intent for legacy transport callers; not an observed capability. */
    void setTypeHint(InfrastructureType hint) {
        this.configuredType = hint == null ? InfrastructureType.NONE : hint;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            // openShiftClient shares the underlying connection; only close the base client
            try {
                kubernetesClient.close();
            } catch (RuntimeException e) {
                LOG.debug("Error closing Kubernetes client");
            }
        }
    }
}
