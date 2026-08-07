package io.github.keycloakmcp.observability.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.config.MetricsConfig;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;

class MetricsQueryBoundsTest {

    @Test
    void acceptsWindowWithinMaxRange() {
        MetricsConfig config = mock(MetricsConfig.class);
        when(config.maxRange()).thenReturn("24h");
        MetricsQueryBounds.validateWindow(MetricWindow.W_15M, config);
    }

    @Test
    void rejectsWindowAboveMaxRange() {
        MetricsConfig config = mock(MetricsConfig.class);
        when(config.maxRange()).thenReturn("5m");
        assertThatThrownBy(() -> MetricsQueryBounds.validateWindow(MetricWindow.W_15M, config))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode())
                        .isEqualTo(ErrorCode.QUERY_RANGE_EXCEEDED));
    }

    @Test
    void computesStepRespectingMaxPoints() {
        Duration step = MetricsQueryBounds.stepFor(MetricWindow.W_1H, 60);
        // 3600 / 59 ≈ 62s
        assertThat(step.getSeconds()).isBetween(1L, 120L);
        long points = 1 + (MetricWindow.W_1H.seconds() / step.getSeconds());
        assertThat(points).isLessThanOrEqualTo(120);
    }

    @Test
    void detectsSeriesLimitExceeded() {
        MetricsConfig config = mock(MetricsConfig.class);
        when(config.maxSeries()).thenReturn(10);
        assertThat(MetricsQueryBounds.exceedsSeriesLimit(11, config)).isTrue();
        assertThat(MetricsQueryBounds.exceedsSeriesLimit(10, config)).isFalse();
    }

    @Test
    void parsesStaleAfter() {
        MetricsConfig config = mock(MetricsConfig.class);
        when(config.staleAfter()).thenReturn("5m");
        assertThat(MetricsQueryBounds.staleAfter(config)).isEqualTo(Duration.ofMinutes(5));
    }
}
