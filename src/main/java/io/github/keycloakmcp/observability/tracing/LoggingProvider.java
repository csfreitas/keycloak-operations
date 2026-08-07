package io.github.keycloakmcp.observability.tracing;

/**
 * Stub for structured log query backends (e.g. Loki). Not a replacement for AuditService.
 */
public interface LoggingProvider {

    boolean supported();
}
