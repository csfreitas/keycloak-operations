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
import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
import io.github.keycloakmcp.domain.inventory.KeycloakWorkloadInfo;
import io.github.keycloakmcp.domain.metrics.PerformanceSummary;
import io.github.keycloakmcp.observability.metrics.MetricAvailability;
import io.github.keycloakmcp.observability.metrics.MetricAvailabilityService;
import io.github.keycloakmcp.observability.metrics.ScrapeReadiness;
import io.github.keycloakmcp.observability.metrics.ServiceMonitorProbe;
import io.github.keycloakmcp.observability.metrics.MetricsProviderStatus;
import io.github.keycloakmcp.service.platform.InventoryService;
import io.github.keycloakmcp.service.platform.MetricsService;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Emits performance evidence from semantic metrics. Never includes secrets or PromQL.
 * STALE / NOT_AVAILABLE metrics do not produce false PASS SLO findings.
 */
@ApplicationScoped
public class MetricsEvidenceCollector implements EvidenceCollector {

    private static final Logger LOG = Logger.getLogger(MetricsEvidenceCollector.class);
    private static final String SOURCE = "metrics";

    private final MetricsService metricsService;
    private final PerformanceConfig performanceConfig;
    private final MetricAvailabilityService availabilityService;
    private final InventoryService inventoryService;
    private final ServiceMonitorProbe serviceMonitorProbe;

    @Inject
    public MetricsEvidenceCollector(
            MetricsService metricsService,
            PerformanceConfig performanceConfig,
            MetricAvailabilityService availabilityService,
            InventoryService inventoryService,
            ServiceMonitorProbe serviceMonitorProbe) {
        this.metricsService = metricsService;
        this.performanceConfig = performanceConfig;
        this.availabilityService = availabilityService;
        this.inventoryService = inventoryService;
        this.serviceMonitorProbe = serviceMonitorProbe;
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
        out.add(ev(targetId, "metrics.http.noTrafficInWindow", http.noTrafficInWindow(), now));

        PerformanceSummary.Database db = summary.database();
        putDouble(out, targetId, "metrics.db.poolAvailable", db.poolAvailable(), now);
        putDouble(out, targetId, "metrics.db.poolActive", db.poolActive(), now);
        putDouble(out, targetId, "metrics.db.poolAwaiting", db.poolAwaiting(), now);
        putDouble(out, targetId, "metrics.db.awaitingCurrent", db.poolAwaiting(), now);
        putDouble(out, targetId, "metrics.db.awaitingAverage", db.poolAwaitingAverage(), now);
        putDouble(out, targetId, "metrics.db.awaitingMax", db.poolAwaitingMax(), now);
        putDouble(out, targetId, "metrics.db.poolUtilization", db.poolUtilization(), now);

        PerformanceSummary.Jvm jvm = summary.jvm();
        putDouble(out, targetId, "metrics.jvm.heapUsedBytes", jvm.heapUsedBytes(), now);
        putDouble(out, targetId, "metrics.jvm.heapCommittedBytes", jvm.heapCommittedBytes(), now);
        putDouble(out, targetId, "metrics.jvm.heapMaxBytes", jvm.heapMaxBytes(), now);
        putDouble(out, targetId, "metrics.jvm.heapUtilization", jvm.heapUtilization(), now);
        putDouble(out, targetId, "metrics.jvm.gcPauseMs", jvm.gcPauseMs(), now);
        putDouble(out, targetId, "metrics.jvm.gcPauseMaxMs", jvm.gcPauseMs(), now);

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

        emitStaleFlags(out, targetId, summary, now);
        emitClusterConsistency(out, target, summary, now);
        emitServiceMonitor(out, target, now);
        emitSloFindings(out, targetId, summary, now);
        return List.copyOf(out);
    }

    private void emitStaleFlags(List<Evidence> out, String targetId, PerformanceSummary summary, Instant now) {
        boolean anyStale = summary.availability().values().stream()
                .anyMatch(a -> a == MetricAvailability.STALE);
        out.add(ev(targetId, "metrics.stale.present", anyStale, now));
    }

    private void emitClusterConsistency(
            List<Evidence> out, Target target, PerformanceSummary summary, Instant now) {
        Double clusterSize = summary.cluster().size();
        if (clusterSize == null || !target.hasInfrastructure()) {
            return;
        }
        try {
            InfrastructureInventory inventory = inventoryService.collect(target.id().value());
            KeycloakWorkloadInfo kc = inventory == null ? null : inventory.keycloak();
            if (kc == null || kc.readyReplicas() < 0) {
                return;
            }
            out.add(ev(target.id().value(), "metrics.cluster.readyReplicas", kc.readyReplicas(), now));
            boolean mismatch = Math.abs(clusterSize - kc.readyReplicas()) >= 0.5;
            out.add(ev(target.id().value(), "metrics.cluster.sizeMismatch", mismatch, now));
        } catch (RuntimeException e) {
            LOG.debugf(e, "Cluster consistency inventory unavailable for target=%s", target.id().value());
        }
    }

    private void emitServiceMonitor(List<Evidence> out, Target target, Instant now) {
        ServiceMonitorProbe.Result r = serviceMonitorProbe.probe(target);
        out.add(ev(target.id().value(), "metrics.scrape.readiness", r.readiness().name(), now));
        if (r.serviceMonitorPresent() != null) {
            out.add(ev(target.id().value(), "metrics.serviceMonitor.present", r.serviceMonitorPresent(), now));
        }
        if (r.interval() != null) {
            out.add(ev(target.id().value(), "metrics.serviceMonitor.interval", r.interval(), now));
        }
        if (r.scrapeTimeout() != null) {
            out.add(ev(target.id().value(), "metrics.serviceMonitor.scrapeTimeout", r.scrapeTimeout(), now));
        }
        if (r.readiness() == ScrapeReadiness.PERMISSION_DENIED) {
            out.add(ev(target.id().value(), "metrics.serviceMonitor.permissionDenied", true, now));
        }
    }

    private void emitSloFindings(List<Evidence> out, String targetId, PerformanceSummary summary, Instant now) {
        boolean anyStale = summary.availability().values().stream()
                .anyMatch(a -> a == MetricAvailability.STALE);

        boolean p99Configured = performanceConfig.latencyP99Ms().isPresent();
        out.add(ev(targetId, "metrics.slo.p99Configured", p99Configured, now));
        if (p99Configured) {
            double slo = performanceConfig.latencyP99Ms().getAsDouble();
            out.add(ev(targetId, "metrics.slo.latencyP99Ms", slo, now));
            out.add(ev(targetId, "performance.policy.latencyP99Ms", slo, now));
            if (!summary.http().histogramAvailable()) {
                out.add(ev(targetId, "metrics.http.histogram.requiredButMissing", true, now));
            } else if (summary.http().noTrafficInWindow()) {
                out.add(ev(targetId, "metrics.http.histogram.requiredButMissing", false, now));
            } else {
                out.add(ev(targetId, "metrics.http.histogram.requiredButMissing", false, now));
                Double p99 = summary.http().p99Ms();
                if (p99 != null && !staleMetric(summary, "HTTP_P99_LATENCY")) {
                    out.add(ev(targetId, "metrics.slo.p99Exceeded", p99 > slo, now));
                }
            }
        }

        boolean p95Configured = performanceConfig.latencyP95Ms().isPresent();
        out.add(ev(targetId, "metrics.slo.p95Configured", p95Configured, now));
        if (p95Configured) {
            double slo = performanceConfig.latencyP95Ms().getAsDouble();
            out.add(ev(targetId, "metrics.slo.latencyP95Ms", slo, now));
            out.add(ev(targetId, "performance.policy.latencyP95Ms", slo, now));
            Double p95 = summary.http().p95Ms();
            if (summary.http().histogramAvailable()
                    && !summary.http().noTrafficInWindow()
                    && p95 != null
                    && !staleMetric(summary, "HTTP_P95_LATENCY")) {
                out.add(ev(targetId, "metrics.slo.p95Exceeded", p95 > slo, now));
            }
        }

        if (performanceConfig.serverErrorRatePercent().isPresent()) {
            double slo = performanceConfig.serverErrorRatePercent().getAsDouble();
            out.add(ev(targetId, "metrics.slo.errorRatePercent", slo, now));
            Double err = summary.http().errorRatePercent();
            if (err != null && !staleMetric(summary, "HTTP_ERROR_RATE")) {
                out.add(ev(targetId, "metrics.slo.errorRateExceeded", err > slo, now));
            }
        }

        emitDbAwaiting(out, targetId, summary, now, anyStale);

        if (performanceConfig.heapUtilizationWarningPercent().isPresent()) {
            double warn = performanceConfig.heapUtilizationWarningPercent().getAsDouble() / 100.0;
            Double util = summary.jvm().heapUtilization();
            if (util != null && !staleMetric(summary, "JVM_HEAP_UTILIZATION")) {
                out.add(ev(targetId, "metrics.jvm.heapPressure", util >= warn, now));
            }
        }

        if (performanceConfig.gcPauseWarningMs().isPresent()) {
            double warn = performanceConfig.gcPauseWarningMs().getAsDouble();
            out.add(ev(targetId, "performance.policy.gcPauseWarningMs", warn, now));
            Double gc = summary.jvm().gcPauseMs();
            if (gc != null && !staleMetric(summary, "JVM_GC_PAUSE")) {
                out.add(ev(targetId, "metrics.jvm.gcPauseExceeded", gc > warn, now));
            }
        }

        if (performanceConfig.minimumCacheHitRatio().isPresent()) {
            double min = performanceConfig.minimumCacheHitRatio().getAsDouble();
            Double hit = summary.cache().hitRatio();
            if (hit != null && !staleMetric(summary, "CACHE_HIT_RATIO")) {
                out.add(ev(targetId, "metrics.cache.hitRatioBelowMinimum", hit < min, now));
            }
        }
    }

    private void emitDbAwaiting(
            List<Evidence> out, String targetId, PerformanceSummary summary, Instant now, boolean anyStale) {
        boolean warnCfg = performanceConfig.dbAwaitingWarning().isPresent();
        boolean critCfg = performanceConfig.dbAwaitingCritical().isPresent();
        if (!warnCfg && !critCfg) {
            return;
        }
        if (staleMetric(summary, "DB_POOL_AWAITING") || staleMetric(summary, "DB_POOL_AWAITING_RANGE")) {
            return;
        }
        Double awaitingMax = summary.database().poolAwaitingMax();
        Double awaitingAvg = summary.database().poolAwaitingAverage();
        Double awaitingCurrent = summary.database().poolAwaiting();
        Double signalMax = awaitingMax != null ? awaitingMax : awaitingCurrent;
        Double signalAvg = awaitingAvg != null ? awaitingAvg : awaitingCurrent;
        if (signalMax == null) {
            return;
        }

        if (critCfg) {
            int crit = performanceConfig.dbAwaitingCritical().getAsInt();
            boolean critical = signalMax >= crit
                    && (signalAvg == null || signalAvg >= Math.min(crit, warnCfg
                            ? performanceConfig.dbAwaitingWarning().orElse(crit)
                            : crit));
            // Sustained: max >= critical AND average >= warning (or critical if warning unset)
            int sustainFloor = warnCfg ? performanceConfig.dbAwaitingWarning().getAsInt() : crit;
            critical = signalMax >= crit && (signalAvg == null || signalAvg >= sustainFloor);
            out.add(ev(targetId, "metrics.db.awaitingCritical", critical, now));
            if (critical) {
                // Prefer critical over warning — avoid duplicate findings
                out.add(ev(targetId, "metrics.db.awaitingWarning", false, now));
                return;
            }
        }

        if (warnCfg) {
            int warn = performanceConfig.dbAwaitingWarning().getAsInt();
            boolean warning = signalMax >= warn
                    && (signalAvg == null || signalAvg >= warn * 0.5);
            out.add(ev(targetId, "metrics.db.awaitingWarning", warning, now));
        }
    }

    private static boolean staleMetric(PerformanceSummary summary, String key) {
        MetricAvailability a = summary.availability().get(key);
        return a == MetricAvailability.STALE;
    }

    private static void putDouble(List<Evidence> out, String targetId, String key, Double value, Instant now) {
        if (value != null) {
            out.add(ev(targetId, key, value, now));
        }
    }

    private static Evidence ev(String targetId, String key, Object value, Instant now) {
        if (value instanceof Map<?, ?> map) {
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
