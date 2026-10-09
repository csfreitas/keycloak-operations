package io.github.keycloakmcp.adapter.infrastructure;

import java.util.Optional;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.github.keycloakmcp.target.InfrastructureType;

/**
 * Thin wrapper around fabric8 Kubernetes/OpenShift clients for a single target.
 * <p>
 * Callers must close the returned client when done (factory manages lifecycle via cache).
 * API availability must come from explicit bounded discovery, never this transport wrapper.
 */
public interface ClusterClient extends AutoCloseable {

    /** Always-available base Kubernetes client (OpenShift is also a Kubernetes cluster). */
    KubernetesClient kubernetes();

    /**
     * Legacy configured OpenShift transport handle; its presence is not observed API support.
     * The OpenShift client is returned as a separate handle; callers should not close it directly.
     */
    @Deprecated
    Optional<OpenShiftClient> openshift();

    /** Namespace derived from config/credentials, or null when not configured. */
    String namespace();

    /** Configured type hint only; NONE when absent. Never probes or confirms runtime identity. */
    @Deprecated
    InfrastructureType type();

    /** Closes the underlying clients. Safe to call multiple times. */
    @Override
    void close();
}
