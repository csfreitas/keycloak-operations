package io.github.keycloakmcp.domain.inventory;

/**
 * Networking exposure for the Keycloak workload.
 */
public record NetworkingInfo(
        /** Whether an OpenShift Route or Kubernetes Ingress is present. */
        boolean routeOrIngressPresent,
        /** Single unambiguous observed host; null when absent or multiple hosts exist. */
        String host,
        /** True only when every associated exposure explicitly configures TLS; not a TLS health check. */
        boolean tlsEnabled,
        java.util.List<ServiceRef> services,
        java.util.List<Exposure> exposures,
        /** Completeness of the supported configuration association, not runtime reachability. */
        boolean complete) {

    public NetworkingInfo(boolean present, String host, boolean tlsEnabled) {
        this(present, host, tlsEnabled, java.util.List.of(), java.util.List.of(), true);
    }

    public NetworkingInfo {
        services = java.util.List.copyOf(services);
        exposures = java.util.List.copyOf(exposures);
    }

    public record ServiceRef(String name, String uid) {}

    /** Allowlisted configured relation only: never certificates, annotations or Secret contents. */
    public record Exposure(String kind, String name, String uid, String serviceName, String serviceUid,
                           String host, String path, Boolean tlsConfigured) {}
}
