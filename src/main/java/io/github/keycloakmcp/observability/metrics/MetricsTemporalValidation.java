package io.github.keycloakmcp.observability.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Validates returned evaluation samples, not the completeness of underlying scrapes. */
public final class MetricsTemporalValidation {
    private MetricsTemporalValidation() {
    }

    public static String instantFailure(List<MetricSeries> series, Instant latestAllowed) {
        if (series == null || series.isEmpty()) return SemanticMetricResult.REASON_NO_SERIES;
        for (MetricSeries item : series) {
            if (item == null || item.samples().size() != 1) return SemanticMetricResult.REASON_INVALID_SAMPLE;
            MetricSample sample = item.samples().getFirst();
            if (!validSample(sample) || sample.timestamp().isAfter(latestAllowed)) {
                return SemanticMetricResult.REASON_INVALID_SAMPLE;
            }
        }
        return null;
    }

    public static String rangeFailure(List<MetricSeries> series, Instant start, Instant end, Duration step) {
        if (series == null || series.isEmpty()) return SemanticMetricResult.REASON_NO_SERIES;
        if (start == null || end == null || step == null || start.isBefore(Instant.EPOCH)
                || end.isBefore(start) || step.isZero() || step.isNegative()
                || start.getNano() != 0 || end.getNano() != 0 || step.getNano() != 0) {
            return SemanticMetricResult.REASON_TEMPORAL_COVERAGE;
        }
        long expectedCount = Duration.between(start, end).getSeconds() / step.getSeconds() + 1;
        for (MetricSeries item : series) {
            if (item == null || item.samples().size() != expectedCount) {
                return SemanticMetricResult.REASON_TEMPORAL_COVERAGE;
            }
            for (int i = 0; i < item.samples().size(); i++) {
                MetricSample sample = item.samples().get(i);
                if (!validSample(sample)) return SemanticMetricResult.REASON_INVALID_SAMPLE;
                // Equality also rejects duplicates, ordering changes, gaps and out-of-window points.
                if (!sample.timestamp().equals(start.plusSeconds(i * step.getSeconds()))) {
                    return SemanticMetricResult.REASON_TEMPORAL_COVERAGE;
                }
            }
        }
        return null;
    }

    private static boolean validSample(MetricSample sample) {
        return sample != null && sample.timestamp() != null && !sample.timestamp().isBefore(Instant.EPOCH)
                && sample.value() != null && Double.isFinite(sample.value());
    }
}
