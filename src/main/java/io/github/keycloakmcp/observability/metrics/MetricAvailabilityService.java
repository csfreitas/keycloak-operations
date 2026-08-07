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
 * Detects presence of key metric series via controlled queries. Results cached briefly.
 * Never coerces missing series to available.
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

    private record CacheEntry(Map<SeriesKey, Boolean> flags, Instant expiresAt) {
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
        if (target == null) {
            return emptyFlags();
        }
        String id = target.id().value();
        CacheEntry cached = cache.get(id);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.flags();
        }
        Map<SeriesKey, Boolean> flags = probe(target);
        int ttl = Math.max(1, metricsConfig.availabilityCacheTtlSeconds());
        cache.put(id, new CacheEntry(flags, now.plusSeconds(ttl)));
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

    private Map<SeriesKey, Boolean> probe(Target target) {
        MetricsProvider provider = providerFactory.forTarget(target);
        MetricWindow window = MetricWindow.defaultWindow();
        Map<SeriesKey, Boolean> flags = emptyFlags();
        if (!provider.supported(target)) {
            return flags;
        }
        flags.put(SeriesKey.HTTP_COUNT, hasValue(provider.query(target, SemanticMetric.HTTP_REQUEST_RATE, window)));
        flags.put(SeriesKey.HTTP_BUCKET, hasValue(provider.query(target, SemanticMetric.HTTP_P99_LATENCY, window)));
        flags.put(SeriesKey.AGROAL, hasValue(provider.query(target, SemanticMetric.DB_POOL_ACTIVE, window)));
        flags.put(SeriesKey.JVM_HEAP, hasValue(provider.query(target, SemanticMetric.JVM_HEAP_USED, window)));
        flags.put(SeriesKey.EVENTS, hasValue(provider.query(target, SemanticMetric.LOGIN_RATE, window)));
        flags.put(SeriesKey.CLUSTER, hasValue(provider.query(target, SemanticMetric.KEYCLOAK_CLUSTER_SIZE, window)));
        return Map.copyOf(flags);
    }

    private static boolean hasValue(SemanticMetricResult result) {
        return result != null
                && result.availability() == MetricAvailability.AVAILABLE
                && result.value() != null;
    }

    private static Map<SeriesKey, Boolean> emptyFlags() {
        Map<SeriesKey, Boolean> m = new EnumMap<>(SeriesKey.class);
        for (SeriesKey key : SeriesKey.values()) {
            m.put(key, false);
        }
        return m;
    }
}
