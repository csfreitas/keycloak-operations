package io.github.keycloakmcp.observability.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MetricWindowTest {

    @Test
    void parsesCanonicalLabels() {
        assertThat(MetricWindow.parse("1m")).isEqualTo(MetricWindow.W_1M);
        assertThat(MetricWindow.parse("5m")).isEqualTo(MetricWindow.W_5M);
        assertThat(MetricWindow.parse("15m")).isEqualTo(MetricWindow.W_15M);
        assertThat(MetricWindow.parse("30m")).isEqualTo(MetricWindow.W_30M);
        assertThat(MetricWindow.parse("1h")).isEqualTo(MetricWindow.W_1H);
        assertThat(MetricWindow.parse("6h")).isEqualTo(MetricWindow.W_6H);
        assertThat(MetricWindow.parse("24h")).isEqualTo(MetricWindow.W_24H);
    }

    @Test
    void blankDefaultsTo5m() {
        assertThat(MetricWindow.parse(null)).isEqualTo(MetricWindow.W_5M);
        assertThat(MetricWindow.parse("  ")).isEqualTo(MetricWindow.W_5M);
        assertThat(MetricWindow.defaultWindow()).isEqualTo(MetricWindow.W_5M);
        assertThat(MetricWindow.assessmentDefault()).isEqualTo(MetricWindow.W_15M);
    }

    @Test
    void rejectsArbitraryRanges() {
        assertThatThrownBy(() -> MetricWindow.parse("2h"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
        assertThat(MetricWindow.tryParse("2h")).isEmpty();
    }
}
