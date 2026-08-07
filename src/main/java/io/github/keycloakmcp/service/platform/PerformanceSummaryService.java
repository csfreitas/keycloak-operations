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
import io.github.keycloakmcp.observability.metrics.SemanticMetric;
import io.github.keycloakmcp.observability.metrics.SemanticMetricResult;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Builds {@link PerformanceSummary} from semantic queries. Missing values stay null.
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
        MetricsProvider provider = providerFactory.forTarget(target);
        MetricsProviderStatus status = provider.status(target);
        String source = sourceLabel(target, provider);

        Map<SemanticMetric, SemanticMetricResult> results = new EnumMap<>(SemanticMetric.class);
        for (SemanticMetric metric : SemanticMetric.values()) {
            results.put(metric, provider.query(target, metric, w));
        }

        boolean histogram = availabilityService.hasHttpBucket(target)
                || value(results.get(SemanticMetric.HTTP_P99_LATENCY)) != null;

        PerformanceSummary.Http http = new PerformanceSummary.Http(
                value(results.get(SemanticMetric.HTTP_REQUEST_RATE)),
                value(results.get(SemanticMetric.HTTP_ERROR_RATE)),
                value(results.get(SemanticMetric.HTTP_AVERAGE_LATENCY)),
                value(results.get(SemanticMetric.HTTP_P50_LATENCY)),
                value(results.get(SemanticMetric.HTTP_P95_LATENCY)),
                value(results.get(SemanticMetric.HTTP_P99_LATENCY)),
                value(results.get(SemanticMetric.HTTP_ACTIVE_REQUESTS)),
                histogram);

        PerformanceSummary.Database database = new PerformanceSummary.Database(
                value(results.get(SemanticMetric.DB_POOL_AVAILABLE)),
                value(results.get(SemanticMetric.DB_POOL_ACTIVE)),
                value(results.get(SemanticMetric.DB_POOL_AWAITING)),
                value(results.get(SemanticMetric.DB_POOL_UTILIZATION)));

        PerformanceSummary.Jvm jvm = new PerformanceSummary.Jvm(
                value(results.get(SemanticMetric.JVM_HEAP_USED)),
                value(results.get(SemanticMetric.JVM_HEAP_COMMITTED)),
                value(results.get(SemanticMetric.JVM_HEAP_MAX)),
                value(results.get(SemanticMetric.JVM_HEAP_UTILIZATION)),
                value(results.get(SemanticMetric.JVM_GC_PAUSE)));

        PerformanceSummary.Cache cache = new PerformanceSummary.Cache(
                value(results.get(SemanticMetric.CACHE_HIT_RATIO)));

        PerformanceSummary.Cluster cluster = new PerformanceSummary.Cluster(
                value(results.get(SemanticMetric.KEYCLOAK_CLUSTER_SIZE)));

        PerformanceSummary.Runtime runtime = new PerformanceSummary.Runtime(
                value(results.get(SemanticMetric.RUNTIME_CPU_USAGE)),
                value(results.get(SemanticMetric.RUNTIME_MEMORY_WORKING_SET)));

        Map<String, MetricAvailability> availability = new LinkedHashMap<>();
        for (Map.Entry<SemanticMetric, SemanticMetricResult> e : results.entrySet()) {
            MetricAvailability a = e.getValue() == null
                    ? MetricAvailability.UNKNOWN
                    : e.getValue().availability();
            availability.put(e.getKey().name(), a);
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
        return providerFactory.forTarget(target).queryCategory(target, category, w);
    }

    public MetricWindow assessmentWindow() {
        return MetricWindow.tryParse(metricsConfig.assessmentWindow()).orElse(MetricWindow.assessmentDefault());
    }

    public MetricWindow interactiveWindow() {
        return MetricWindow.tryParse(metricsConfig.defaultWindow()).orElse(MetricWindow.defaultWindow());
    }

    private static Double value(SemanticMetricResult result) {
        if (result == null || result.availability() != MetricAvailability.AVAILABLE) {
            return null;
        }
        return result.value();
    }

    private static String sourceLabel(Target target, MetricsProvider provider) {
        if (target.observability() != null && target.observability().metricsType() != null) {
            return target.observability().metricsType();
        }
        return provider.getClass().getSimpleName();
    }
}
