package io.github.keycloakmcp.target;

/**
 * Optional cluster / VM infrastructure settings for a target.
 * Holds a credential reference only — never secrets.
 */
public record InfrastructureTargetConfiguration(
        InfrastructureType type,
        String clusterId,
        String namespace,
        String credentialRef,
        KubernetesInstallationBinding installation) {

    public InfrastructureTargetConfiguration(InfrastructureType type, String clusterId, String namespace, String credentialRef) {
        this(type, clusterId, namespace, credentialRef, null);
    }

    public InfrastructureTargetConfiguration {
        if (installation != null && ((type != InfrastructureType.KUBERNETES && type != InfrastructureType.OPENSHIFT)
                || clusterId == null || clusterId.isBlank() || namespace == null || namespace.isBlank()
                || credentialRef == null || credentialRef.isBlank())) {
            throw new IllegalArgumentException("An installation requires explicit cluster identity, namespace and credentials");
        }
    }
}
