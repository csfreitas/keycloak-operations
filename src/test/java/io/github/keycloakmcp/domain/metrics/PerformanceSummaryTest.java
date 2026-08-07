package io.github.keycloakmcp.domain.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.observability.metrics.MetricAvailability;
import io.github.keycloakmcp.observability.metrics.MetricWindow;
import io.github.keycloakmcp.observability.metrics.MetricsProviderStatus;

class PerformanceSummaryTest {

    @Test
    void missingValuesStayNullNeverZero() {
        PerformanceSummary summary = new PerformanceSummary(
                "t1",
                MetricWindow.W_5M,
                MetricsProviderStatus.AVAILABLE,
                "prometheus",
                Instant.now(),
                PerformanceSummary.Http.empty(),
                PerformanceSummary.Database.empty(),
                PerformanceSummary.Jvm.empty(),
                PerformanceSummary.Cache.empty(),
                PerformanceSummary.Cluster.empty(),
                PerformanceSummary.Runtime.empty(),
                Map.of("HTTP_REQUEST_RATE", MetricAvailability.NOT_AVAILABLE));

        assertThat(summary.http().requestRate()).isNull();
        assertThat(summary.http().p99Ms()).isNull();
        assertThat(summary.database().poolAwaiting()).isNull();
        assertThat(summary.jvm().heapUtilization()).isNull();
        assertThat(summary.http().asNullableMap().get("requestRate")).isNull();
        assertThat(summary.availability().get("HTTP_REQUEST_RATE")).isEqualTo(MetricAvailability.NOT_AVAILABLE);
    }

    @Test
    void assessmentWindowDefaultIs15m() {
        assertThat(MetricWindow.assessmentDefault()).isEqualTo(MetricWindow.W_15M);
        assertThat(MetricWindow.defaultWindow()).isEqualTo(MetricWindow.W_5M);
    }
}
