package io.github.keycloakmcp.observability.metrics;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.keycloakmcp.config.MetricsConfig;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Detects presence of key metric series via controlled probes. Results cached briefly.
 * Histogram presence uses bucket series count — not whether p99 returned a value.
 */
@ApplicationScoped
public class MetricAvailabilityService {

    public enum SeriesKey {
        HTTP_COUNT,
        HTTP_BUCKET,
        AGROAL,
        JVM_HEAP,
        EVENTS,
        CLUSTER
    }

    private record CacheEntry(Target target, Map<SeriesKey, Boolean> flags, Instant expiresAt) {
    }

    private final MetricsConfig metricsConfig;
    private final MetricsProviderFactory providerFactory;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Inject
    public MetricAvailabilityService(MetricsConfig metricsConfig, MetricsProviderFactory providerFactory) {
        this.metricsConfig = metricsConfig;
        this.providerFactory = providerFactory;
    }

    public Map<SeriesKey, Boolean> detect(Target target) {
        return detect(target, MetricsOperationBudget.fromConfig(metricsConfig));
    }

    /** Only observed keys are returned. A missing key means unknown, not false. */
    public Map<SeriesKey, Boolean> detect(Target target, MetricsOperationBudget budget) {
        if (target == null || budget.exhausted()) {
            return Map.of();
        }
        String id = target.id().value();
        CacheEntry cached = cache.get(id);
        Instant now = Instant.now();
        if (cached != null && cached.target().equals(target) && cached.expiresAt().isAfter(now)) {
            return cached.flags();
        }
        Map<SeriesKey, Boolean> flags = probe(target, budget);
        int ttl = Math.max(1, metricsConfig.availabilityCacheTtlSeconds());
        // Failed/aborted probes are not negative observations and must not poison the cache.
        if (!budget.exhausted() && flags.size() == SeriesKey.values().length) {
            cache.put(id, new CacheEntry(target, flags, Instant.now().plusSeconds(ttl)));
        }
        return flags;
    }

    public boolean hasHttpCount(Target target) {
        return Boolean.TRUE.equals(detect(target).get(SeriesKey.HTTP_COUNT));
    }

    public boolean hasHttpBucket(Target target) {
        return Boolean.TRUE.equals(detect(target).get(SeriesKey.HTTP_BUCKET));
    }

    public void invalidate(String targetId) {
        if (targetId != null) {
            cache.remove(targetId);
        }
    }

    private Map<SeriesKey, Boolean> probe(Target target, MetricsOperationBudget budget) {
        MetricsProvider provider = providerFactory.forTarget(target);
        Map<SeriesKey, Boolean> flags = new EnumMap<>(SeriesKey.class);
        if (!provider.supported(target)) {
            return flags;
        }
        String[] families = { "http_server_requests_seconds_count", "http_server_requests_seconds_bucket",
                "agroal_active_count", "jvm_memory_used_bytes", "keycloak_user_events_total", "vendor_cluster_size" };
        for (SeriesKey key : SeriesKey.values()) {
            if (budget.exhausted()) break;
            SemanticMetricResult result = provider.probeSeries(target, families[key.ordinal()], budget);
            if (budget.exhausted()) break;
            if (result != null && result.availability() == MetricAvailability.AVAILABLE
                    && result.value() != null && Double.isFinite(result.value()) && result.value() >= 0) {
                flags.put(key, result.value() > 0);
            } else if (result != null && result.availability() == MetricAvailability.NOT_AVAILABLE
                    && SemanticMetricResult.REASON_NO_SERIES.equals(result.reason())) {
                flags.put(key, false);
            }
        }
        return Map.copyOf(flags);
    }
}
