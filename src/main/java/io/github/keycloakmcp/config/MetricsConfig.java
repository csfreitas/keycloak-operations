package io.github.keycloakmcp.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "metrics")
public interface MetricsConfig {

    @WithName("max-range")
    @WithDefault("24h")
    String maxRange();

    @WithName("max-series")
    @WithDefault("500")
    int maxSeries();

    @WithName("max-points")
    @WithDefault("1000")
    int maxPoints();

    @WithName("connect-timeout-ms")
    @WithDefault("3000")
    int connectTimeoutMs();

    @WithName("read-timeout-ms")
    @WithDefault("10000")
    int readTimeoutMs();

    @WithName("availability-cache-ttl-seconds")
    @WithDefault("60")
    int availabilityCacheTtlSeconds();

    @WithName("default-window")
    @WithDefault("5m")
    String defaultWindow();

    @WithName("assessment-window")
    @WithDefault("15m")
    String assessmentWindow();

    /**
     * Samples older than this are marked {@code STALE} and must not drive PASS findings.
     */
    @WithName("stale-after")
    @WithDefault("5m")
    String staleAfter();
}
