package io.github.keycloakmcp.observability.tracing;

/**
 * Stub for future tracing backend integration (OpenTelemetry exporters already available via Quarkus).
 */
public interface TracingProvider {

    boolean supported();
}
