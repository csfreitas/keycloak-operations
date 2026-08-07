package io.github.keycloakmcp.adapter.infrastructure;

import java.util.Optional;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.github.keycloakmcp.target.InfrastructureType;

/**
 * Thin wrapper around fabric8 Kubernetes/OpenShift clients for a single target.
 * <p>
 * Callers must close the returned client when done (factory manages lifecycle via cache).
 * Use {@link #type()} to determine which specific API surface is available.
 */
public interface ClusterClient extends AutoCloseable {

    /** Always-available base Kubernetes client (OpenShift is also a Kubernetes cluster). */
    KubernetesClient kubernetes();

    /**
     * OpenShift-specific client, present only when {@link #type()} is {@link InfrastructureType#OPENSHIFT}.
     * The OpenShift client is returned as a separate handle; callers should not close it directly.
     */
    Optional<OpenShiftClient> openshift();

    /** Namespace derived from config/credentials, or null when not configured. */
    String namespace();

    /** Whether the underlying cluster is OPENSHIFT or KUBERNETES. */
    InfrastructureType type();

    /** Closes the underlying clients. Safe to call multiple times. */
    @Override
    void close();
}
