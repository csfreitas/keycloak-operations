package io.github.keycloakmcp.domain.inventory;

/**
 * Networking exposure for the Keycloak workload.
 */
public record NetworkingInfo(
        /** Whether an OpenShift Route or Kubernetes Ingress is present. */
        boolean routeOrIngressPresent,
        /** Route/Ingress host (first hostname found); null if absent. */
        String host,
        /** Whether TLS is configured on the route/ingress. */
        boolean tlsEnabled) {
}
