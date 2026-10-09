package io.github.keycloakmcp.discovery;

/**
 * Fixed API versions advertised by a complete, bounded API-group observation.
 * Advertisement is not authorization, a successful resource read or target health.
 */
public record ClusterApiCapabilities(ApiAvailability routeV1, ApiAvailability configV1) {
    public enum ApiAvailability { SERVED, NOT_SERVED, UNSUPPORTED_VERSION, UNKNOWN }

    public ClusterApiCapabilities {
        routeV1 = routeV1 == null ? ApiAvailability.UNKNOWN : routeV1;
        configV1 = configV1 == null ? ApiAvailability.UNKNOWN : configV1;
    }

    public static ClusterApiCapabilities unknown() {
        return new ClusterApiCapabilities(ApiAvailability.UNKNOWN, ApiAvailability.UNKNOWN);
    }
}
