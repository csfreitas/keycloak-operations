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
        Map<SeriesKey, Boolean> flags = emptyFlags();
        if (!provider.supported(target)) {
            return flags;
        }
        flags.put(SeriesKey.HTTP_COUNT, seriesPresent(provider.probeSeries(target, "http_server_requests_seconds_count")));
        flags.put(SeriesKey.HTTP_BUCKET, seriesPresent(provider.probeSeries(target, "http_server_requests_seconds_bucket")));
        flags.put(SeriesKey.AGROAL, seriesPresent(provider.probeSeries(target, "agroal_active_count")));
        flags.put(SeriesKey.JVM_HEAP, seriesPresent(provider.probeSeries(target, "jvm_memory_used_bytes")));
        flags.put(SeriesKey.EVENTS, seriesPresent(provider.probeSeries(target, "keycloak_user_events_total")));
        flags.put(SeriesKey.CLUSTER, seriesPresent(provider.probeSeries(target, "vendor_cluster_size")));
        return Map.copyOf(flags);
    }

    private static boolean seriesPresent(SemanticMetricResult result) {
        return result != null
                && (result.availability() == MetricAvailability.AVAILABLE
                        || result.availability() == MetricAvailability.STALE)
                && result.value() != null
                && result.value() > 0;
    }

    private static Map<SeriesKey, Boolean> emptyFlags() {
        Map<SeriesKey, Boolean> m = new EnumMap<>(SeriesKey.class);
        for (SeriesKey key : SeriesKey.values()) {
            m.put(key, false);
        }
        return m;
    }
}
