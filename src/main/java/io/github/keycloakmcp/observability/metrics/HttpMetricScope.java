package io.github.keycloakmcp.observability.metrics;

/**
 * Controlled HTTP URI scopes — never arbitrary regex from callers.
 */
public enum HttpMetricScope {
    ALL,
    AUTHENTICATION,
    TOKEN,
    LOGIN
}
