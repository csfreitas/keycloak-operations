package io.github.keycloakmcp.observability.metrics;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SemanticMetricResult(
        String targetId,
        SemanticMetric metric,
        MetricWindow window,
        Double value,
        String unit,
        MetricAvailability availability,
        String reason,
        String source,
        Instant collectedAt,
        int seriesCount,
        Instant lastSampleTimestamp,
        List<Map<String, String>> labels) {

    public static final String REASON_NO_TRAFFIC = "NO_TRAFFIC";
    public static final String REASON_HISTOGRAM_NOT_AVAILABLE = "HTTP_HISTOGRAM_NOT_ENABLED";
    public static final String REASON_SERIES_LIMIT = "LIMIT_EXCEEDED";
    public static final String REASON_STALE = "STALE";

    public SemanticMetricResult {
        labels = labels == null ? List.of() : List.copyOf(labels);
    }

    public static SemanticMetricResult notConfigured(String targetId, SemanticMetric metric, MetricWindow window) {
        return new SemanticMetricResult(
                targetId, metric, window, null, null, MetricAvailability.NOT_CONFIGURED,
                "Metrics provider not configured for target", null, Instant.now(), 0, null, List.of());
    }

    public static SemanticMetricResult notAvailable(
            String targetId, SemanticMetric metric, MetricWindow window, String source, String reason) {
        return new SemanticMetricResult(
                targetId, metric, window, null, null, MetricAvailability.NOT_AVAILABLE,
                reason, source, Instant.now(), 0, null, List.of());
    }

    public static SemanticMetricResult limitExceeded(
            String targetId, SemanticMetric metric, MetricWindow window, String source, int seriesCount) {
        return new SemanticMetricResult(
                targetId, metric, window, null, null, MetricAvailability.NOT_AVAILABLE,
                REASON_SERIES_LIMIT, source, Instant.now(), seriesCount, null, List.of());
    }

    public static SemanticMetricResult available(
            String targetId,
            SemanticMetric metric,
            MetricWindow window,
            Double value,
            String unit,
            String source,
            int seriesCount,
            Instant lastSampleTimestamp,
            List<Map<String, String>> labels) {
        return new SemanticMetricResult(
                targetId, metric, window, value, unit, MetricAvailability.AVAILABLE,
                null, source, Instant.now(), seriesCount, lastSampleTimestamp, labels);
    }

    public static SemanticMetricResult stale(
            String targetId,
            SemanticMetric metric,
            MetricWindow window,
            Double value,
            String unit,
            String source,
            int seriesCount,
            Instant lastSampleTimestamp,
            List<Map<String, String>> labels) {
        return new SemanticMetricResult(
                targetId, metric, window, value, unit, MetricAvailability.STALE,
                REASON_STALE, source, Instant.now(), seriesCount, lastSampleTimestamp, labels);
    }

    /** True when the result may drive performance PASS/FAIL findings. */
    public boolean usableForFindings() {
        return availability == MetricAvailability.AVAILABLE && value != null;
    }
}
