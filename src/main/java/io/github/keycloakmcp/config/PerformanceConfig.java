package io.github.keycloakmcp.config;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * Optional performance SLO / warning thresholds for assessment evidence.
 * Unset options mean the corresponding SLO finding is not emitted as configured.
 */
@ConfigMapping(prefix = "assessment.performance")
public interface PerformanceConfig {

    @WithName("latency-p99-ms")
    OptionalDouble latencyP99Ms();

    @WithName("latency-p95-ms")
    OptionalDouble latencyP95Ms();

    @WithName("server-error-rate-percent")
    OptionalDouble serverErrorRatePercent();

    @WithName("db-awaiting-warning")
    OptionalInt dbAwaitingWarning();

    @WithName("db-awaiting-critical")
    OptionalInt dbAwaitingCritical();

    @WithName("heap-utilization-warning-percent")
    OptionalDouble heapUtilizationWarningPercent();

    @WithName("gc-pause-warning-ms")
    OptionalDouble gcPauseWarningMs();

    @WithName("minimum-cache-hit-ratio")
    OptionalDouble minimumCacheHitRatio();
}
