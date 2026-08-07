package io.github.keycloakmcp.target;

/**
 * Optional cluster / VM infrastructure settings for a target.
 * Holds a credential reference only — never secrets.
 */
public record InfrastructureTargetConfiguration(
        InfrastructureType type,
        String clusterId,
        String namespace,
        String credentialRef) {
}
