package io.github.keycloakmcp.observability.metrics;

import java.time.Instant;
import java.util.Map;

public record MetricSample(
        Instant timestamp,
        Double value,
        Map<String, String> labels) {

    public MetricSample {
        labels = labels == null ? Map.of() : Map.copyOf(labels);
    }
}
