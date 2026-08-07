package io.github.keycloakmcp.observability.metrics;

/**
 * Distinguishes metrics enabled vs actually scraped (OpenShift Monitoring).
 */
public enum ScrapeReadiness {
    METRICS_DISABLED,
    SERVICEMONITOR_MISSING,
    SCRAPE_TARGET_DOWN,
    SCRAPE_HEALTHY,
    UNKNOWN,
    PERMISSION_DENIED
}
