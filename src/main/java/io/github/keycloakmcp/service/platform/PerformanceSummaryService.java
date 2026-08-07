package io.github.keycloakmcp.service.platform;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.keycloakmcp.config.MetricsConfig;
import io.github.keycloakmcp.domain.metrics.PerformanceSummary;
import io.github.keycloakmcp.observability.metrics.MetricAvailability;
import io.github.keycloakmcp.observability.metrics.MetricAvailabilityService;
import io.github.keycloakmcp.observability.metrics.MetricCategory;
import io.github.keycloakmcp.observability.metrics.MetricWindow;
import io.github.keycloakmcp.observability.metrics.MetricsProvider;
import io.github.keycloakmcp.observability.metrics.MetricsProviderFactory;
import io.github.keycloakmcp.observability.metrics.MetricsProviderStatus;
import io.github.keycloakmcp.observability.metrics.MetricsQueryBounds;
import io.github.keycloakmcp.observability.metrics.RangeMetricSummary;
import io.github.keycloakmcp.observability.metrics.SemanticMetric;
import io.github.keycloakmcp.observability.metrics.SemanticMetricResult;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Builds {@link PerformanceSummary} from semantic queries. Missing values stay null.
 * STALE results are exposed in availability but not used as usable numeric values.
 */
@ApplicationScoped
public class PerformanceSummaryService {

    private final MetricsProviderFactory providerFactory;
    private final MetricAvailabilityService availabilityService;
    private final MetricsConfig metricsConfig;

    @Inject
    public PerformanceSummaryService(
            MetricsProviderFactory providerFactory,
            MetricAvailabilityService availabilityService,
            MetricsConfig metricsConfig) {
        this.providerFactory = providerFactory;
        this.availabilityService = availabilityService;
        this.metricsConfig = metricsConfig;
    }

    public PerformanceSummary summarize(Target target, MetricWindow window) {
        MetricWindow w = window == null ? assessmentWindow() : window;
        MetricsQueryBounds.validateWindow(w, metricsConfig);
        MetricsProvider provider = providerFactory.forTarget(target);
        MetricsProviderStatus status = provider.status(target);
        String source = sourceLabel(target, provider);

        Map<SemanticMetric, SemanticMetricResult> results = new EnumMap<>(SemanticMetric.class);
        for (SemanticMetric metric : SemanticMetric.values()) {
            results.put(metric, provider.query(target, metric, w));
        }

        boolean histogramPresent = availabilityService.hasHttpBucket(target);
        boolean noTraffic = histogramPresent
                && usable(results.get(SemanticMetric.HTTP_P99_LATENCY)) == null
                && percentileUnavailableDueToNoTraffic(results.get(SemanticMetric.HTTP_P99_LATENCY));

        // Rewrite percentile availability reasons when histogram exists but window has no observations
        if (histogramPresent) {
            rewriteNoTraffic(results, SemanticMetric.HTTP_P50_LATENCY);
            rewriteNoTraffic(results, SemanticMetric.HTTP_P95_LATENCY);
            rewriteNoTraffic(results, SemanticMetric.HTTP_P99_LATENCY);
        }

        PerformanceSummary.Http http = new PerformanceSummary.Http(
                usable(results.get(SemanticMetric.HTTP_REQUEST_RATE)),
                usable(results.get(SemanticMetric.HTTP_ERROR_RATE)),
                usable(results.get(SemanticMetric.HTTP_AVERAGE_LATENCY)),
                usable(results.get(SemanticMetric.HTTP_P50_LATENCY)),
                usable(results.get(SemanticMetric.HTTP_P95_LATENCY)),
                usable(results.get(SemanticMetric.HTTP_P99_LATENCY)),
                usable(results.get(SemanticMetric.HTTP_ACTIVE_REQUESTS)),
                histogramPresent,
                noTraffic);

        RangeMetricSummary awaitingRange = provider.queryRange(target, SemanticMetric.DB_POOL_AWAITING, w);
        Double awaitingCurrent = awaitingRange.availability() == MetricAvailability.AVAILABLE
                ? awaitingRange.current()
                : usable(results.get(SemanticMetric.DB_POOL_AWAITING));
        Double awaitingAvg = awaitingRange.availability() == MetricAvailability.AVAILABLE
                ? awaitingRange.average()
                : null;
        Double awaitingMax = awaitingRange.availability() == MetricAvailability.AVAILABLE
                ? awaitingRange.max()
                : null;

        PerformanceSummary.Database database = new PerformanceSummary.Database(
                usable(results.get(SemanticMetric.DB_POOL_AVAILABLE)),
                usable(results.get(SemanticMetric.DB_POOL_ACTIVE)),
                awaitingCurrent,
                awaitingAvg,
                awaitingMax,
                usable(results.get(SemanticMetric.DB_POOL_UTILIZATION)));

        PerformanceSummary.Jvm jvm = new PerformanceSummary.Jvm(
                usable(results.get(SemanticMetric.JVM_HEAP_USED)),
                usable(results.get(SemanticMetric.JVM_HEAP_COMMITTED)),
                usable(results.get(SemanticMetric.JVM_HEAP_MAX)),
                usable(results.get(SemanticMetric.JVM_HEAP_UTILIZATION)),
                usable(results.get(SemanticMetric.JVM_GC_PAUSE)));

        PerformanceSummary.Cache cache = new PerformanceSummary.Cache(
                usable(results.get(SemanticMetric.CACHE_HIT_RATIO)));

        PerformanceSummary.Cluster cluster = new PerformanceSummary.Cluster(
                usable(results.get(SemanticMetric.KEYCLOAK_CLUSTER_SIZE)));

        PerformanceSummary.Runtime runtime = new PerformanceSummary.Runtime(
                usable(results.get(SemanticMetric.RUNTIME_CPU_USAGE)),
                usable(results.get(SemanticMetric.RUNTIME_MEMORY_WORKING_SET)));

        Map<String, MetricAvailability> availability = new LinkedHashMap<>();
        for (Map.Entry<SemanticMetric, SemanticMetricResult> e : results.entrySet()) {
            MetricAvailability a = e.getValue() == null
                    ? MetricAvailability.UNKNOWN
                    : e.getValue().availability();
            availability.put(e.getKey().name(), a);
        }
        if (awaitingRange.availability() != null) {
            availability.put("DB_POOL_AWAITING_RANGE", awaitingRange.availability());
        }

        return new PerformanceSummary(
                target.id().value(),
                w,
                status,
                source,
                Instant.now(),
                http,
                database,
                jvm,
                cache,
                cluster,
                runtime,
                availability);
    }

    public List<SemanticMetricResult> category(Target target, MetricCategory category, MetricWindow window) {
        MetricWindow w = window == null ? interactiveWindow() : window;
        MetricsQueryBounds.validateWindow(w, metricsConfig);
        return providerFactory.forTarget(target).queryCategory(target, category, w);
    }

    public MetricWindow assessmentWindow() {
        return MetricWindow.tryParse(metricsConfig.assessmentWindow()).orElse(MetricWindow.assessmentDefault());
    }

    public MetricWindow interactiveWindow() {
        return MetricWindow.tryParse(metricsConfig.defaultWindow()).orElse(MetricWindow.defaultWindow());
    }

    private static Double usable(SemanticMetricResult result) {
        if (result == null || !result.usableForFindings()) {
            return null;
        }
        return result.value();
    }

    private static boolean percentileUnavailableDueToNoTraffic(SemanticMetricResult result) {
        if (result == null) {
            return false;
        }
        if (result.availability() != MetricAvailability.NOT_AVAILABLE) {
            return false;
        }
        String reason = result.reason();
        return reason == null
                || reason.contains("No time series")
                || SemanticMetricResult.REASON_NO_TRAFFIC.equals(reason);
    }

    private static void rewriteNoTraffic(Map<SemanticMetric, SemanticMetricResult> results, SemanticMetric metric) {
        SemanticMetricResult r = results.get(metric);
        if (r == null || r.availability() != MetricAvailability.NOT_AVAILABLE) {
            return;
        }
        if (r.value() != null) {
            return;
        }
        results.put(metric, SemanticMetricResult.notAvailable(
                r.targetId(), metric, r.window(), r.source(), SemanticMetricResult.REASON_NO_TRAFFIC));
    }

    private static String sourceLabel(Target target, MetricsProvider provider) {
        if (target.observability() != null && target.observability().metricsType() != null) {
            return target.observability().metricsType();
        }
        return provider.getClass().getSimpleName();
    }
}
