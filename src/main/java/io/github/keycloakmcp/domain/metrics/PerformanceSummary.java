package io.github.keycloakmcp.domain.metrics;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.keycloakmcp.observability.metrics.MetricAvailability;
import io.github.keycloakmcp.observability.metrics.MetricWindow;
import io.github.keycloakmcp.observability.metrics.MetricsProviderStatus;

/**
 * Aggregated performance view. Missing metrics stay {@code null} — never coerced to 0.
 */
public record PerformanceSummary(
        String targetId,
        MetricWindow window,
        MetricsProviderStatus providerStatus,
        String source,
        Instant collectedAt,
        Http http,
        Database database,
        Jvm jvm,
        Cache cache,
        Cluster cluster,
        Runtime runtime,
        Map<String, MetricAvailability> availability) {

    public PerformanceSummary {
        availability = availability == null ? Map.of() : Map.copyOf(availability);
        http = http == null ? Http.empty() : http;
        database = database == null ? Database.empty() : database;
        jvm = jvm == null ? Jvm.empty() : jvm;
        cache = cache == null ? Cache.empty() : cache;
        cluster = cluster == null ? Cluster.empty() : cluster;
        runtime = runtime == null ? Runtime.empty() : runtime;
    }

    public record Http(
            Double requestRate,
            Double errorRatePercent,
            Double averageLatencyMs,
            Double p50Ms,
            Double p95Ms,
            Double p99Ms,
            Double activeRequests,
            boolean histogramAvailable) {

        public static Http empty() {
            return new Http(null, null, null, null, null, null, null, false);
        }

        public Map<String, Double> asNullableMap() {
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("requestRate", requestRate);
            m.put("errorRatePercent", errorRatePercent);
            m.put("averageLatencyMs", averageLatencyMs);
            m.put("p50Ms", p50Ms);
            m.put("p95Ms", p95Ms);
            m.put("p99Ms", p99Ms);
            m.put("activeRequests", activeRequests);
            return m;
        }
    }

    public record Database(
            Double poolAvailable,
            Double poolActive,
            Double poolAwaiting,
            Double poolUtilization) {

        public static Database empty() {
            return new Database(null, null, null, null);
        }

        public Map<String, Double> asNullableMap() {
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("poolAvailable", poolAvailable);
            m.put("poolActive", poolActive);
            m.put("poolAwaiting", poolAwaiting);
            m.put("poolUtilization", poolUtilization);
            return m;
        }
    }

    public record Jvm(
            Double heapUsedBytes,
            Double heapCommittedBytes,
            Double heapMaxBytes,
            Double heapUtilization,
            Double gcPauseMs) {

        public static Jvm empty() {
            return new Jvm(null, null, null, null, null);
        }

        public Map<String, Double> asNullableMap() {
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("heapUsedBytes", heapUsedBytes);
            m.put("heapCommittedBytes", heapCommittedBytes);
            m.put("heapMaxBytes", heapMaxBytes);
            m.put("heapUtilization", heapUtilization);
            m.put("gcPauseMs", gcPauseMs);
            return m;
        }
    }

    public record Cache(Double hitRatio) {

        public static Cache empty() {
            return new Cache(null);
        }

        public Map<String, Double> asNullableMap() {
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("hitRatio", hitRatio);
            return m;
        }
    }

    public record Cluster(Double size) {

        public static Cluster empty() {
            return new Cluster(null);
        }

        public Map<String, Double> asNullableMap() {
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("size", size);
            return m;
        }
    }

    public record Runtime(Double cpuUsage, Double memoryWorkingSetBytes) {

        public static Runtime empty() {
            return new Runtime(null, null);
        }

        public Map<String, Double> asNullableMap() {
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("cpuUsage", cpuUsage);
            m.put("memoryWorkingSetBytes", memoryWorkingSetBytes);
            return m;
        }
    }
}
