package io.github.keycloakmcp.collector.metrics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.collector.EvidenceCollector;
import io.github.keycloakmcp.config.PerformanceConfig;
import io.github.keycloakmcp.domain.metrics.PerformanceSummary;
import io.github.keycloakmcp.observability.metrics.MetricAvailabilityService;
import io.github.keycloakmcp.observability.metrics.MetricsProviderStatus;
import io.github.keycloakmcp.service.platform.MetricsService;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Emits performance evidence from semantic metrics. Never includes secrets or PromQL.
 */
@ApplicationScoped
public class MetricsEvidenceCollector implements EvidenceCollector {

    private static final Logger LOG = Logger.getLogger(MetricsEvidenceCollector.class);
    private static final String SOURCE = "metrics";

    private final MetricsService metricsService;
    private final PerformanceConfig performanceConfig;
    private final MetricAvailabilityService availabilityService;

    @Inject
    public MetricsEvidenceCollector(
            MetricsService metricsService,
            PerformanceConfig performanceConfig,
            MetricAvailabilityService availabilityService) {
        this.metricsService = metricsService;
        this.performanceConfig = performanceConfig;
        this.availabilityService = availabilityService;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public List<Evidence> collect(Target target) {
        if (target == null || !target.hasMetrics()) {
            return List.of();
        }
        Instant now = Instant.now();
        String targetId = target.id().value();
        PerformanceSummary summary;
        try {
            summary = metricsService.summaryForAssessment(target);
        } catch (RuntimeException e) {
            LOG.warnf(e, "Metrics summary failed for target=%s", targetId);
            throw e;
        }

        List<Evidence> out = new ArrayList<>();
        boolean sourceAvailable = summary.providerStatus() == MetricsProviderStatus.AVAILABLE
                || summary.providerStatus() == MetricsProviderStatus.DEGRADED;
        out.add(ev(targetId, "metrics.source.available", sourceAvailable, now));
        out.add(ev(targetId, "metrics.window", summary.window().label(), now));
        out.add(ev(targetId, "metrics.source", summary.source(), now));
        out.add(ev(targetId, "metrics.provider.status", summary.providerStatus().name(), now));

        PerformanceSummary.Http http = summary.http();
        putDouble(out, targetId, "metrics.http.requestRate", http.requestRate(), now);
        putDouble(out, targetId, "metrics.http.errorRatePercent", http.errorRatePercent(), now);
        putDouble(out, targetId, "metrics.http.averageLatencyMs", http.averageLatencyMs(), now);
        putDouble(out, targetId, "metrics.http.p50Ms", http.p50Ms(), now);
        putDouble(out, targetId, "metrics.http.p95Ms", http.p95Ms(), now);
        putDouble(out, targetId, "metrics.http.p99Ms", http.p99Ms(), now);
        putDouble(out, targetId, "metrics.http.activeRequests", http.activeRequests(), now);
        out.add(ev(targetId, "metrics.http.histogram.available", http.histogramAvailable(), now));

        PerformanceSummary.Database db = summary.database();
        putDouble(out, targetId, "metrics.db.poolAvailable", db.poolAvailable(), now);
        putDouble(out, targetId, "metrics.db.poolActive", db.poolActive(), now);
        putDouble(out, targetId, "metrics.db.poolAwaiting", db.poolAwaiting(), now);
        putDouble(out, targetId, "metrics.db.poolUtilization", db.poolUtilization(), now);

        PerformanceSummary.Jvm jvm = summary.jvm();
        putDouble(out, targetId, "metrics.jvm.heapUsedBytes", jvm.heapUsedBytes(), now);
        putDouble(out, targetId, "metrics.jvm.heapCommittedBytes", jvm.heapCommittedBytes(), now);
        putDouble(out, targetId, "metrics.jvm.heapMaxBytes", jvm.heapMaxBytes(), now);
        putDouble(out, targetId, "metrics.jvm.heapUtilization", jvm.heapUtilization(), now);
        putDouble(out, targetId, "metrics.jvm.gcPauseMs", jvm.gcPauseMs(), now);

        putDouble(out, targetId, "metrics.cache.hitRatio", summary.cache().hitRatio(), now);
        putDouble(out, targetId, "metrics.cluster.size", summary.cluster().size(), now);
        putDouble(out, targetId, "metrics.runtime.cpuUsage", summary.runtime().cpuUsage(), now);
        putDouble(out, targetId, "metrics.runtime.memoryWorkingSetBytes", summary.runtime().memoryWorkingSetBytes(), now);

        Map<MetricAvailabilityService.SeriesKey, Boolean> series = availabilityService.detect(target);
        out.add(ev(targetId, "metrics.series.httpCount", Boolean.TRUE.equals(series.get(MetricAvailabilityService.SeriesKey.HTTP_COUNT)), now));
        out.add(ev(targetId, "metrics.series.httpBucket", Boolean.TRUE.equals(series.get(MetricAvailabilityService.SeriesKey.HTTP_BUCKET)), now));
        out.add(ev(targetId, "metrics.series.agroal", Boolean.TRUE.equals(series.get(MetricAvailabilityService.SeriesKey.AGROAL)), now));
        out.add(ev(targetId, "metrics.series.jvmHeap", Boolean.TRUE.equals(series.get(MetricAvailabilityService.SeriesKey.JVM_HEAP)), now));
        out.add(ev(targetId, "metrics.series.events", Boolean.TRUE.equals(series.get(MetricAvailabilityService.SeriesKey.EVENTS)), now));
        out.add(ev(targetId, "metrics.series.cluster", Boolean.TRUE.equals(series.get(MetricAvailabilityService.SeriesKey.CLUSTER)), now));

        emitSloFindings(out, targetId, summary, now);
        return List.copyOf(out);
    }

    private void emitSloFindings(List<Evidence> out, String targetId, PerformanceSummary summary, Instant now) {
        boolean p99Configured = performanceConfig.latencyP99Ms().isPresent();
        out.add(ev(targetId, "metrics.slo.p99Configured", p99Configured, now));
        if (p99Configured) {
            double slo = performanceConfig.latencyP99Ms().getAsDouble();
            out.add(ev(targetId, "metrics.slo.latencyP99Ms", slo, now));
            Double p99 = summary.http().p99Ms();
            if (p99 == null) {
                out.add(ev(targetId, "metrics.http.histogram.requiredButMissing", true, now));
            } else {
                out.add(ev(targetId, "metrics.http.histogram.requiredButMissing", false, now));
                out.add(ev(targetId, "metrics.slo.p99Exceeded", p99 > slo, now));
            }
        }

        if (performanceConfig.serverErrorRatePercent().isPresent()) {
            double slo = performanceConfig.serverErrorRatePercent().getAsDouble();
            out.add(ev(targetId, "metrics.slo.errorRatePercent", slo, now));
            Double err = summary.http().errorRatePercent();
            if (err != null) {
                out.add(ev(targetId, "metrics.slo.errorRateExceeded", err > slo, now));
            }
        }

        if (performanceConfig.dbAwaitingWarning().isPresent()) {
            int warn = performanceConfig.dbAwaitingWarning().getAsInt();
            Double awaiting = summary.database().poolAwaiting();
            if (awaiting != null) {
                out.add(ev(targetId, "metrics.db.awaitingWarning", awaiting >= warn, now));
            }
        }

        if (performanceConfig.heapUtilizationWarningPercent().isPresent()) {
            double warn = performanceConfig.heapUtilizationWarningPercent().getAsDouble() / 100.0;
            Double util = summary.jvm().heapUtilization();
            if (util != null) {
                out.add(ev(targetId, "metrics.jvm.heapPressure", util >= warn, now));
            }
        }

        if (performanceConfig.minimumCacheHitRatio().isPresent()) {
            double min = performanceConfig.minimumCacheHitRatio().getAsDouble();
            Double hit = summary.cache().hitRatio();
            if (hit != null) {
                out.add(ev(targetId, "metrics.cache.hitRatioBelowMinimum", hit < min, now));
            }
        }
    }

    private static void putDouble(List<Evidence> out, String targetId, String key, Double value, Instant now) {
        if (value != null) {
            out.add(ev(targetId, key, value, now));
        }
    }

    private static Evidence ev(String targetId, String key, Object value, Instant now) {
        if (value instanceof Map<?, ?> map) {
            // Provenance-safe complex values: copy without secrets
            Map<String, Object> safe = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    safe.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            return new Evidence(targetId, SOURCE, "performance", key, Map.copyOf(safe), now);
        }
        return new Evidence(targetId, SOURCE, "performance", key, value, now);
    }
}
