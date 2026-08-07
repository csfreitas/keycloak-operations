package io.github.keycloakmcp.observability.metrics;

import java.util.List;
import java.util.Map;

public record MetricSeries(
        String name,
        Map<String, String> labels,
        List<MetricSample> samples) {

    public MetricSeries {
        labels = labels == null ? Map.of() : Map.copyOf(labels);
        samples = samples == null ? List.of() : List.copyOf(samples);
    }
}
