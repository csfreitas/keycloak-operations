package io.github.keycloakmcp.observability.metrics;

import java.time.Instant;
import java.util.Set;

/** Counts validated matching up observations, not expected targets or raw scrape coverage. */
public record ScrapeObservation(
        MetricAvailability availability,
        Integer observed,
        Integer successful,
        Integer failed,
        Instant evaluatedAt,
        String reason) {

    private static final Set<String> SAFE_REASONS = Set.of(
            "SCRAPE_PROBE_UNSUPPORTED", "NOT_CONFIGURED", "NO_TIME_SERIES", "INVALID_SAMPLE",
            "SCOPE_MISMATCH", "LIMIT_EXCEEDED", "STALE", "UNAUTHORIZED", "FORBIDDEN",
            "TIMED_OUT", "RATE_LIMITED", "ENDPOINT_NOT_FOUND", "MALFORMED_RESPONSE",
            "INCOMPLETE_RESPONSE", "METRICS_BACKEND_FAILED", "OPERATION_BUDGET_EXCEEDED", "OPERATION_INTERRUPTED");

    public ScrapeObservation {
        if (availability == MetricAvailability.AVAILABLE) {
            if (observed == null || successful == null || failed == null || observed < 1
                    || successful < 0 || failed < 0 || (long) successful + failed != observed
                    || evaluatedAt == null || evaluatedAt.isBefore(Instant.EPOCH) || evaluatedAt.isAfter(Instant.now())) {
                availability = MetricAvailability.NOT_AVAILABLE;
                reason = "INVALID_SAMPLE";
            } else {
                reason = null;
            }
        } else if (availability == null) {
            availability = MetricAvailability.NOT_AVAILABLE;
        }
        if (availability != MetricAvailability.AVAILABLE) {
            observed = null;
            successful = null;
            failed = null;
            evaluatedAt = null;
            reason = reason != null && SAFE_REASONS.contains(reason) ? reason : "METRICS_BACKEND_FAILED";
        }
    }

    public static ScrapeObservation unavailable(String reason) {
        return new ScrapeObservation(MetricAvailability.NOT_AVAILABLE, null, null, null, null, reason);
    }

    public static ScrapeObservation notConfigured() {
        return new ScrapeObservation(MetricAvailability.NOT_CONFIGURED, null, null, null, null, "NOT_CONFIGURED");
    }

    public static ScrapeObservation stale() {
        return new ScrapeObservation(MetricAvailability.STALE, null, null, null, null, "STALE");
    }
}
