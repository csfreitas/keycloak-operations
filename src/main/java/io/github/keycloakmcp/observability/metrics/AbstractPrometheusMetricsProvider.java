package io.github.keycloakmcp.observability.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.config.MetricsConfig;
import io.github.keycloakmcp.credential.CredentialProvider;
import io.github.keycloakmcp.credential.MetricsCredentials;
import io.github.keycloakmcp.observability.metrics.prometheus.PrometheusApiClient;
import io.github.keycloakmcp.target.ObservabilityTargetConfiguration;
import io.github.keycloakmcp.target.Target;

/**
 * Shared query/status logic for Prometheus-compatible backends.
 */
public abstract class AbstractPrometheusMetricsProvider implements MetricsProvider {

    private static final Set<String> ALLOWED_PROBE_FAMILIES = Set.of(
            "http_server_requests_seconds_count",
            "http_server_requests_seconds_bucket",
            "agroal_active_count",
            "jvm_memory_used_bytes",
            "keycloak_user_events_total",
            "vendor_cluster_size",
            "up");

    private final Logger log;
    private final PrometheusApiClient apiClient;
    private final MetricsEndpointResolver endpointResolver;
    private final CredentialProvider credentialProvider;
    private final MetricsConfig metricsConfig;
    private final String sourceName;

    protected AbstractPrometheusMetricsProvider(
            Logger log,
            PrometheusApiClient apiClient,
            MetricsEndpointResolver endpointResolver,
            CredentialProvider credentialProvider,
            MetricsConfig metricsConfig,
            String sourceName) {
        this.log = log;
        this.apiClient = apiClient;
        this.endpointResolver = endpointResolver;
        this.credentialProvider = credentialProvider;
        this.metricsConfig = metricsConfig;
        this.sourceName = sourceName;
    }

    @Override
    public boolean supported(Target target) {
        return matchesType(target) && endpointResolver.resolve(target).isPresent();
    }

    @Override
    public MetricsProviderStatus status(Target target) {
        if (!matchesType(target)) {
            return MetricsProviderStatus.NOT_CONFIGURED;
        }
        Optional<String> endpoint = endpointResolver.resolve(target);
        if (endpoint.isEmpty()) {
            return MetricsProviderStatus.NOT_CONFIGURED;
        }
        MetricsQueryContext ctx = endpointResolver.queryContext(target);
        String promQl = "up{" + ctx.selectorClause() + "}";
        PrometheusApiClient.Response response = apiClient.query(
                endpoint.get(),
                promQl,
                resolveCredentials(target),
                connectTimeout(),
                readTimeout());
        return mapStatus(response.status());
    }

    @Override
    public SemanticMetricResult query(Target target, SemanticMetric metric, MetricWindow window) {
        if (target == null || metric == null) {
            return SemanticMetricResult.notConfigured(null, metric, window);
        }
        MetricWindow w = window == null ? MetricWindow.defaultWindow() : window;
        MetricsQueryBounds.validateWindow(w, metricsConfig);
        if (!matchesType(target)) {
            return SemanticMetricResult.notConfigured(target.id().value(), metric, w);
        }
        Optional<String> endpoint = endpointResolver.resolve(target);
        if (endpoint.isEmpty()) {
            return SemanticMetricResult.notConfigured(target.id().value(), metric, w);
        }

        MetricsQueryContext ctx = endpointResolver.queryContext(target);
        String promQl = MetricsQueryBuilder.build(metric, w, ctx);
        log.debugf("Querying %s semantic=%s window=%s", sourceName, metric, w.label());

        PrometheusApiClient.Response response = apiClient.query(
                endpoint.get(),
                promQl,
                resolveCredentials(target),
                connectTimeout(),
                readTimeout());

        return toResult(target.id().value(), metric, w, response);
    }

    @Override
    public List<SemanticMetricResult> queryCategory(Target target, MetricCategory category, MetricWindow window) {
        MetricWindow w = window == null ? MetricWindow.defaultWindow() : window;
        MetricsQueryBounds.validateWindow(w, metricsConfig);
        List<SemanticMetricResult> out = new ArrayList<>();
        for (SemanticMetric metric : MetricsCatalog.forCategory(category)) {
            out.add(query(target, metric, w));
        }
        return List.copyOf(out);
    }

    @Override
    public SemanticMetricResult probeSeries(Target target, String controlledMetricFamily) {
        if (target == null || controlledMetricFamily == null || controlledMetricFamily.isBlank()) {
            return SemanticMetricResult.notAvailable(null, null, MetricWindow.defaultWindow(), sourceName, "Invalid probe");
        }
        if (!ALLOWED_PROBE_FAMILIES.contains(controlledMetricFamily)) {
            return SemanticMetricResult.notAvailable(
                    target.id().value(), null, MetricWindow.defaultWindow(), sourceName, "Probe family not allowed");
        }
        if (!matchesType(target)) {
            return SemanticMetricResult.notConfigured(target.id().value(), null, MetricWindow.defaultWindow());
        }
        Optional<String> endpoint = endpointResolver.resolve(target);
        if (endpoint.isEmpty()) {
            return SemanticMetricResult.notConfigured(target.id().value(), null, MetricWindow.defaultWindow());
        }
        MetricsQueryContext ctx = endpointResolver.queryContext(target);
        String promQl = MetricsQueryBuilder.countSeries(controlledMetricFamily, ctx);
        PrometheusApiClient.Response response = apiClient.query(
                endpoint.get(),
                promQl,
                resolveCredentials(target),
                connectTimeout(),
                readTimeout());
        return toResult(target.id().value(), null, MetricWindow.defaultWindow(), response);
    }

    @Override
    public RangeMetricSummary queryRange(Target target, SemanticMetric metric, MetricWindow window) {
        if (target == null || metric == null) {
            return RangeMetricSummary.notAvailable("Invalid arguments");
        }
        MetricWindow w = window == null ? MetricWindow.defaultWindow() : window;
        MetricsQueryBounds.validateWindow(w, metricsConfig);
        if (!matchesType(target)) {
            return RangeMetricSummary.notAvailable("Provider not configured");
        }
        Optional<String> endpoint = endpointResolver.resolve(target);
        if (endpoint.isEmpty()) {
            return RangeMetricSummary.notAvailable("Endpoint not configured");
        }

        MetricsQueryContext ctx = endpointResolver.queryContext(target);
        String promQl = MetricsQueryBuilder.instantGauge(metric, ctx);
        Instant end = Instant.now();
        Instant start = end.minusSeconds(w.seconds());
        Duration step = MetricsQueryBounds.stepFor(w, metricsConfig.maxPoints());

        PrometheusApiClient.Response response = apiClient.queryRange(
                endpoint.get(),
                promQl,
                start,
                end,
                step,
                resolveCredentials(target),
                connectTimeout(),
                readTimeout());

        if (response.status() != PrometheusApiClient.Status.OK) {
            SemanticMetricResult fallback = query(target, metric, w);
            return RangeMetricSummary.fromInstant(fallback);
        }
        List<MetricSeries> series = response.series();
        if (MetricsQueryBounds.exceedsSeriesLimit(series.size(), metricsConfig)) {
            return new RangeMetricSummary(
                    null, null, null, MetricAvailability.NOT_AVAILABLE,
                    SemanticMetricResult.REASON_SERIES_LIMIT, series.size(), 0);
        }
        if (series.isEmpty()) {
            return RangeMetricSummary.notAvailable("No time series returned");
        }

        List<Double> values = new ArrayList<>();
        for (MetricSeries s : series) {
            if (s.samples() == null) {
                continue;
            }
            for (MetricSample sample : s.samples()) {
                if (sample != null && sample.value() != null) {
                    values.add(sample.value());
                }
            }
        }
        if (values.isEmpty()) {
            return RangeMetricSummary.notAvailable("No samples in window");
        }
        double sum = 0;
        double max = Double.NEGATIVE_INFINITY;
        for (Double v : values) {
            sum += v;
            max = Math.max(max, v);
        }
        Double current = values.get(values.size() - 1);
        double average = sum / values.size();
        Instant lastTs = lastTimestamp(series);
        MetricAvailability availability = MetricAvailability.AVAILABLE;
        String reason = null;
        Duration staleAfter = MetricsQueryBounds.staleAfter(metricsConfig);
        if (lastTs != null && Duration.between(lastTs, Instant.now()).compareTo(staleAfter) > 0) {
            availability = MetricAvailability.STALE;
            reason = SemanticMetricResult.REASON_STALE;
        }
        return new RangeMetricSummary(
                current, average, max, availability, reason, series.size(), values.size());
    }

    protected abstract boolean matchesType(Target target);

    protected MetricsCredentials resolveCredentials(Target target) {
        ObservabilityTargetConfiguration obs = target.observability();
        if (obs == null || obs.credentialRef() == null || obs.credentialRef().isBlank()) {
            return MetricsCredentials.none();
        }
        try {
            return credentialProvider.getMetricsCredentials(obs.credentialRef());
        } catch (RuntimeException e) {
            log.debugf("Metrics credential resolve failed for target=%s", target.id().value());
            return MetricsCredentials.none();
        }
    }

    private SemanticMetricResult toResult(
            String targetId, SemanticMetric metric, MetricWindow window, PrometheusApiClient.Response response) {
        MetricsCatalog.Entry entry = metric == null ? null : MetricsCatalog.entry(metric);
        String unit = entry == null ? null : entry.unit();
        return switch (response.status()) {
            case OK -> {
                List<MetricSeries> series = response.series();
                if (MetricsQueryBounds.exceedsSeriesLimit(series.size(), metricsConfig)) {
                    yield SemanticMetricResult.limitExceeded(
                            targetId, metric, window, sourceName, series.size());
                }
                Double value = firstValue(series);
                Instant lastTs = lastTimestamp(series);
                List<Map<String, String>> labels = series.stream().map(MetricSeries::labels).toList();
                Duration staleAfter = MetricsQueryBounds.staleAfter(metricsConfig);
                if (lastTs != null && Duration.between(lastTs, Instant.now()).compareTo(staleAfter) > 0) {
                    yield SemanticMetricResult.stale(
                            targetId, metric, window, value, unit, sourceName, series.size(), lastTs, labels);
                }
                yield SemanticMetricResult.available(
                        targetId, metric, window, value, unit, sourceName, series.size(), lastTs, labels);
            }
            case EMPTY -> SemanticMetricResult.notAvailable(
                    targetId, metric, window, sourceName, "No time series returned");
            case UNAUTHORIZED -> SemanticMetricResult.notAvailable(
                    targetId, metric, window, sourceName, "Unauthorized");
            case FORBIDDEN -> SemanticMetricResult.notAvailable(
                    targetId, metric, window, sourceName, "Forbidden");
            case TIMEOUT -> SemanticMetricResult.notAvailable(
                    targetId, metric, window, sourceName, "Timed out");
            case RATE_LIMITED -> SemanticMetricResult.notAvailable(
                    targetId, metric, window, sourceName, "Rate limited");
            case NOT_FOUND -> SemanticMetricResult.notAvailable(
                    targetId, metric, window, sourceName, "Endpoint not found");
            case MALFORMED -> SemanticMetricResult.notAvailable(
                    targetId, metric, window, sourceName, "Malformed response");
            case SERVER_ERROR, NETWORK_ERROR -> SemanticMetricResult.notAvailable(
                    targetId, metric, window, sourceName,
                    response.message() == null ? "Backend error" : response.message());
        };
    }

    private static MetricsProviderStatus mapStatus(PrometheusApiClient.Status status) {
        return switch (status) {
            case OK, EMPTY -> MetricsProviderStatus.AVAILABLE;
            case UNAUTHORIZED, FORBIDDEN -> MetricsProviderStatus.UNAUTHORIZED;
            case TIMEOUT, RATE_LIMITED, SERVER_ERROR, NETWORK_ERROR, MALFORMED -> MetricsProviderStatus.DEGRADED;
            case NOT_FOUND -> MetricsProviderStatus.UNAVAILABLE;
        };
    }

    private static Double firstValue(List<MetricSeries> series) {
        if (series == null) {
            return null;
        }
        for (MetricSeries s : series) {
            if (s.samples() == null) {
                continue;
            }
            for (int i = s.samples().size() - 1; i >= 0; i--) {
                MetricSample sample = s.samples().get(i);
                if (sample != null && sample.value() != null) {
                    return sample.value();
                }
            }
        }
        return null;
    }

    private static Instant lastTimestamp(List<MetricSeries> series) {
        Instant best = null;
        if (series == null) {
            return null;
        }
        for (MetricSeries s : series) {
            if (s.samples() == null || s.samples().isEmpty()) {
                continue;
            }
            MetricSample last = s.samples().get(s.samples().size() - 1);
            if (last != null && last.timestamp() != null
                    && (best == null || last.timestamp().isAfter(best))) {
                best = last.timestamp();
            }
        }
        return best;
    }

    private Duration connectTimeout() {
        return Duration.ofMillis(Math.max(100, metricsConfig.connectTimeoutMs()));
    }

    private Duration readTimeout() {
        return Duration.ofMillis(Math.max(100, metricsConfig.readTimeoutMs()));
    }
}
