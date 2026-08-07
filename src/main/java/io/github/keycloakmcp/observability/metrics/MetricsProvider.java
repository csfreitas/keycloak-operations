package io.github.keycloakmcp.observability.metrics;

public interface MetricsProvider {

    MetricsResult query(MetricsQuery query);

    boolean supported();
}
