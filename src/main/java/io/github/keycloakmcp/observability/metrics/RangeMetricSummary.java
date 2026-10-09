package io.github.keycloakmcp.observability.metrics;

/**
 * Temporal summary for a metric over a window (from query_range when available).
 */
public record RangeMetricSummary(
        Double current,
        Double average,
        Double max,
        MetricAvailability availability,
        String reason,
        int seriesCount,
        int sampleCount) {

    public static RangeMetricSummary notAvailable(String reason) {
        return new RangeMetricSummary(null, null, null, MetricAvailability.NOT_AVAILABLE, reason, 0, 0);
    }

    public static RangeMetricSummary fromInstant(SemanticMetricResult instant) {
        // A current sample cannot establish an average/maximum over a requested window.
        return notAvailable("Temporal metrics unavailable; an instant sample is not a range summary");
    }
}
