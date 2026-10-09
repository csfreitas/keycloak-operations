package io.github.keycloakmcp.observability.metrics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

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
        return status(target, MetricsOperationBudget.fromConfig(metricsConfig));
    }

    @Override
    public MetricsProviderStatus status(Target target, MetricsOperationBudget budget) {
        if (budget.exhausted()) return MetricsProviderStatus.DEGRADED;
        if (!matchesType(target)) {
            return MetricsProviderStatus.NOT_CONFIGURED;
        }
        Optional<String> endpoint = endpointResolver.resolve(target);
        if (budget.exhausted()) return MetricsProviderStatus.DEGRADED;
        if (endpoint.isEmpty()) {
            return MetricsProviderStatus.NOT_CONFIGURED;
        }
        MetricsQueryContext ctx = endpointResolver.queryContext(target);
        String promQl = "up{" + ctx.selectorClause() + "}";
        if (budget.exhausted()) return MetricsProviderStatus.DEGRADED;
        MetricsCredentials credentials = resolveCredentials(target);
        if (budget.exhausted()) return MetricsProviderStatus.DEGRADED;
        if (credentials == null) return MetricsProviderStatus.UNAUTHORIZED;
        PrometheusApiClient.Response response = apiClient.query(
                endpoint.get(),
                promQl,
                credentials,
                connectTimeout(),
                readTimeout(), budget);
        if (budget.exhausted()) return MetricsProviderStatus.DEGRADED;
        return response == null || response.status() == null ? MetricsProviderStatus.DEGRADED : mapStatus(response.status());
    }

    @Override
    public SemanticMetricResult query(Target target, SemanticMetric metric, MetricWindow window) {
        return query(target, metric, window, MetricsOperationBudget.fromConfig(metricsConfig));
    }

    @Override
    public SemanticMetricResult query(Target target, SemanticMetric metric, MetricWindow window, MetricsOperationBudget budget) {
        if (budget.exhausted()) return aborted(target, metric, window, budget);
        if (target == null || metric == null) {
            return SemanticMetricResult.notConfigured(null, metric, window);
        }
        MetricWindow w = window == null ? MetricWindow.defaultWindow() : window;
        MetricsQueryBounds.validateWindow(w, metricsConfig);
        if (!matchesType(target)) {
            return SemanticMetricResult.notConfigured(target.id().value(), metric, w);
        }
        Optional<String> endpoint = endpointResolver.resolve(target);
        if (budget.exhausted()) return aborted(target, metric, w, budget);
        if (endpoint.isEmpty()) {
            return SemanticMetricResult.notConfigured(target.id().value(), metric, w);
        }

        MetricsQueryContext ctx = endpointResolver.queryContext(target);
        String promQl = MetricsQueryBuilder.build(metric, w, ctx);
        log.debugf("Querying %s semantic=%s window=%s", sourceName, metric, w.label());
        if (budget.exhausted()) return aborted(target, metric, w, budget);
        MetricsCredentials credentials = resolveCredentials(target);
        if (budget.exhausted()) return aborted(target, metric, w, budget);
        if (credentials == null) return unauthorized(target, metric, w);

        PrometheusApiClient.Response response = apiClient.query(
                endpoint.get(),
                promQl,
                credentials,
                connectTimeout(),
                readTimeout(), budget);

        if (budget.exhausted()) return aborted(target, metric, w, budget);
        SemanticMetricResult result = toResult(target.id().value(), metric, w, response);
        return budget.exhausted() ? aborted(target, metric, w, budget) : result;
    }

    @Override
    public List<SemanticMetricResult> queryCategory(Target target, MetricCategory category, MetricWindow window) {
        return queryCategory(target, category, window, MetricsOperationBudget.fromConfig(metricsConfig));
    }

    @Override
    public List<SemanticMetricResult> queryCategory(Target target, MetricCategory category, MetricWindow window,
            MetricsOperationBudget budget) {
        MetricWindow w = window == null ? MetricWindow.defaultWindow() : window;
        MetricsQueryBounds.validateWindow(w, metricsConfig);
        List<SemanticMetricResult> out = new ArrayList<>();
        for (SemanticMetric metric : MetricsCatalog.forCategory(category)) {
            out.add(budget.exhausted() ? aborted(target, metric, w, budget) : query(target, metric, w, budget));
        }
        return List.copyOf(out);
    }

    @Override
    public SemanticMetricResult probeSeries(Target target, String controlledMetricFamily) {
        return probeSeries(target, controlledMetricFamily, MetricsOperationBudget.fromConfig(metricsConfig));
    }

    @Override
    public SemanticMetricResult probeSeries(Target target, String controlledMetricFamily, MetricsOperationBudget budget) {
        if (budget.exhausted()) return aborted(target, null, MetricWindow.defaultWindow(), budget);
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
        if (budget.exhausted()) return aborted(target, null, MetricWindow.defaultWindow(), budget);
        if (endpoint.isEmpty()) {
            return SemanticMetricResult.notConfigured(target.id().value(), null, MetricWindow.defaultWindow());
        }
        MetricsQueryContext ctx = endpointResolver.queryContext(target);
        String promQl = MetricsQueryBuilder.countSeries(controlledMetricFamily, ctx);
        if (budget.exhausted()) return aborted(target, null, MetricWindow.defaultWindow(), budget);
        MetricsCredentials credentials = resolveCredentials(target);
        if (budget.exhausted()) return aborted(target, null, MetricWindow.defaultWindow(), budget);
        if (credentials == null) return unauthorized(target, null, MetricWindow.defaultWindow());
        PrometheusApiClient.Response response = apiClient.query(
                endpoint.get(),
                promQl,
                credentials,
                connectTimeout(),
                readTimeout(), budget);
        if (budget.exhausted()) return aborted(target, null, MetricWindow.defaultWindow(), budget);
        SemanticMetricResult result = toResult(target.id().value(), null, MetricWindow.defaultWindow(), response);
        return budget.exhausted() ? aborted(target, null, MetricWindow.defaultWindow(), budget) : result;
    }

    @Override
    public RangeMetricSummary queryRange(Target target, SemanticMetric metric, MetricWindow window) {
        return queryRange(target, metric, window, MetricsOperationBudget.fromConfig(metricsConfig));
    }

    @Override
    public ScrapeObservation probeScrape(Target target) {
        return probeScrape(target, MetricsOperationBudget.fromConfig(metricsConfig));
    }

    @Override
    public ScrapeObservation probeScrape(Target target, MetricsOperationBudget budget) {
        if (budget.exhausted()) return ScrapeObservation.unavailable(budget.reason());
        if (target == null || !matchesType(target)) return ScrapeObservation.notConfigured();
        try {
            Optional<String> endpoint = endpointResolver.resolve(target);
            if (budget.exhausted()) return ScrapeObservation.unavailable(budget.reason());
            if (endpoint.isEmpty()) return ScrapeObservation.notConfigured();
            MetricsQueryContext ctx = MetricsQueryBuilder.scrapeContext(
                    target.id().value(), endpointResolver.queryContext(target));
            String promQl = MetricsQueryBuilder.scrapeUp(ctx);
            if (budget.exhausted()) return ScrapeObservation.unavailable(budget.reason());
            MetricsCredentials credentials = resolveCredentials(target);
            if (budget.exhausted()) return ScrapeObservation.unavailable(budget.reason());
            if (credentials == null) return ScrapeObservation.unavailable("UNAUTHORIZED");
            PrometheusApiClient.Response response = apiClient.query(endpoint.get(), promQl, credentials,
                    connectTimeout(), readTimeout(), budget);
            if (budget.exhausted()) return ScrapeObservation.unavailable(budget.reason());
            ScrapeObservation result = scrapeObservation(response, ctx);
            return budget.exhausted() ? ScrapeObservation.unavailable(budget.reason()) : result;
        } catch (RuntimeException e) {
            if (budget.exhausted()) return ScrapeObservation.unavailable(budget.reason());
            log.debug("Controlled scrape observation unavailable");
            return ScrapeObservation.unavailable("METRICS_BACKEND_FAILED");
        }
    }

    private ScrapeObservation scrapeObservation(PrometheusApiClient.Response response, MetricsQueryContext ctx) {
        if (response == null || response.status() == null) return ScrapeObservation.unavailable("INVALID_SAMPLE");
        if (response.status() != PrometheusApiClient.Status.OK) {
            return ScrapeObservation.unavailable(switch (response.status()) {
                case EMPTY -> "NO_TIME_SERIES";
                case UNAUTHORIZED -> "UNAUTHORIZED";
                case FORBIDDEN -> "FORBIDDEN";
                case TIMEOUT -> "TIMED_OUT";
                case OPERATION_ABORTED -> MetricsOperationBudget.REASON_INTERRUPTED.equals(response.message())
                        ? MetricsOperationBudget.REASON_INTERRUPTED : MetricsOperationBudget.REASON_EXCEEDED;
                case RATE_LIMITED -> "RATE_LIMITED";
                case NOT_FOUND -> "ENDPOINT_NOT_FOUND";
                case LIMIT_EXCEEDED -> "LIMIT_EXCEEDED";
                case MALFORMED -> "MALFORMED_RESPONSE";
                case INCOMPLETE -> "INCOMPLETE_RESPONSE";
                default -> "METRICS_BACKEND_FAILED";
            });
        }
        List<MetricSeries> series = response.series();
        if (series == null) return ScrapeObservation.unavailable("INVALID_SAMPLE");
        if (series.isEmpty()) return ScrapeObservation.unavailable("NO_TIME_SERIES");
        if (MetricsQueryBounds.exceedsSeriesLimit(series.size(), metricsConfig)) {
            return ScrapeObservation.unavailable("LIMIT_EXCEEDED");
        }
        Instant now = Instant.now();
        Duration staleAfter = MetricsQueryBounds.staleAfter(metricsConfig);
        Set<Map<String, String>> labelSets = new HashSet<>();
        Instant evaluatedAt = null;
        int successful = 0;
        boolean stale = false;
        for (MetricSeries item : series) {
            if (item == null || !"up".equals(item.name()) || item.samples().size() != 1
                    || !labelSets.add(item.labels())) return ScrapeObservation.unavailable("INVALID_SAMPLE");
            for (Map.Entry<String, String> expected : ctx.mandatoryLabels().entrySet()) {
                if (!expected.getValue().equals(item.labels().get(expected.getKey()))) {
                    return ScrapeObservation.unavailable("SCOPE_MISMATCH");
                }
            }
            if (ctx.scope() == MetricsScope.NAMESPACE && ctx.namespace() != null && !ctx.namespace().isBlank()
                    && !ctx.namespace().equals(item.labels().get("namespace"))) {
                return ScrapeObservation.unavailable("SCOPE_MISMATCH");
            }
            MetricSample sample = item.samples().getFirst();
            if (sample == null || sample.timestamp() == null || sample.timestamp().isBefore(Instant.EPOCH)
                    || sample.timestamp().isAfter(now) || sample.value() == null || !Double.isFinite(sample.value())
                    || (sample.value() != 0d && sample.value() != 1d) || !sample.labels().equals(item.labels())) {
                return ScrapeObservation.unavailable("INVALID_SAMPLE");
            }
            if (evaluatedAt == null) evaluatedAt = sample.timestamp();
            else if (!evaluatedAt.equals(sample.timestamp())) return ScrapeObservation.unavailable("INVALID_SAMPLE");
            stale |= Duration.between(sample.timestamp(), now).compareTo(staleAfter) > 0;
            if (sample.value() == 1d) successful++;
        }
        if (stale) return ScrapeObservation.stale();
        return new ScrapeObservation(MetricAvailability.AVAILABLE, series.size(), successful,
                series.size() - successful, evaluatedAt, null);
    }

    @Override
    public RangeMetricSummary queryRange(Target target, SemanticMetric metric, MetricWindow window, MetricsOperationBudget budget) {
        if (budget.exhausted()) return RangeMetricSummary.notAvailable(budget.reason());
        if (target == null || metric == null) {
            return RangeMetricSummary.notAvailable("Invalid arguments");
        }
        MetricWindow w = window == null ? MetricWindow.defaultWindow() : window;
        MetricsQueryBounds.validateWindow(w, metricsConfig);
        if (!matchesType(target)) {
            return RangeMetricSummary.notAvailable("Provider not configured");
        }
        Optional<String> endpoint = endpointResolver.resolve(target);
        if (budget.exhausted()) return RangeMetricSummary.notAvailable(budget.reason());
        if (endpoint.isEmpty()) {
            return RangeMetricSummary.notAvailable("Endpoint not configured");
        }

        MetricsQueryContext ctx = endpointResolver.queryContext(target);
        String promQl = MetricsQueryBuilder.instantGauge(metric, ctx);
        Instant end = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant start = end.minusSeconds(w.seconds());
        Duration step = MetricsQueryBounds.stepFor(w, metricsConfig.maxPoints());
        if (budget.exhausted()) return RangeMetricSummary.notAvailable(budget.reason());
        MetricsCredentials credentials = resolveCredentials(target);
        if (budget.exhausted()) return RangeMetricSummary.notAvailable(budget.reason());
        if (credentials == null) return RangeMetricSummary.notAvailable("Unauthorized");

        PrometheusApiClient.Response response = apiClient.queryRange(
                endpoint.get(),
                promQl,
                start,
                end,
                step,
                credentials,
                connectTimeout(),
                readTimeout(), budget);

        if (budget.exhausted()) return RangeMetricSummary.notAvailable(budget.reason());
        if (response == null || response.status() != PrometheusApiClient.Status.OK) {
            return RangeMetricSummary.notAvailable(toResult(target.id().value(), metric, w, response).reason());
        }
        List<MetricSeries> series = response.series();
        if (series == null) return RangeMetricSummary.notAvailable(SemanticMetricResult.REASON_INVALID_SAMPLE);
        if (MetricsQueryBounds.exceedsSeriesLimit(series.size(), metricsConfig)) {
            return new RangeMetricSummary(
                    null, null, null, MetricAvailability.NOT_AVAILABLE,
                    SemanticMetricResult.REASON_SERIES_LIMIT, series.size(), 0);
        }
        if (series.isEmpty()) {
            return RangeMetricSummary.notAvailable(SemanticMetricResult.REASON_NO_SERIES);
        }
        for (MetricSeries s : series) {
            if (s == null) return RangeMetricSummary.notAvailable(SemanticMetricResult.REASON_INVALID_SAMPLE);
            if (s.samples().size() > Math.max(2, metricsConfig.maxPoints())) {
                return RangeMetricSummary.notAvailable(SemanticMetricResult.REASON_SERIES_LIMIT);
            }
        }
        // All controlled semantic queries produce one aggregate series. Do not select a favorable subset.
        if (series.size() != 1) {
            return RangeMetricSummary.notAvailable(SemanticMetricResult.REASON_UNEXPECTED_SERIES);
        }
        String failure = MetricsTemporalValidation.rangeFailure(series, start, end, step);
        if (failure != null) return RangeMetricSummary.notAvailable(failure);
        List<MetricSample> samples = series.getFirst().samples();
        BigDecimal sum = BigDecimal.ZERO;
        double max = Double.NEGATIVE_INFINITY;
        for (MetricSample sample : samples) {
            sum = sum.add(BigDecimal.valueOf(sample.value()));
            max = Math.max(max, sample.value());
        }
        MetricSample last = samples.getLast();
        double average = sum.divide(BigDecimal.valueOf(samples.size()), MathContext.DECIMAL128).doubleValue();
        if (!Double.isFinite(average)) return RangeMetricSummary.notAvailable(SemanticMetricResult.REASON_INVALID_SAMPLE);
        MetricAvailability availability = MetricAvailability.AVAILABLE;
        String reason = null;
        Duration staleAfter = MetricsQueryBounds.staleAfter(metricsConfig);
        if (Duration.between(last.timestamp(), Instant.now()).compareTo(staleAfter) > 0) {
            availability = MetricAvailability.STALE;
            reason = SemanticMetricResult.REASON_STALE;
        }
        if (budget.exhausted()) return RangeMetricSummary.notAvailable(budget.reason());
        return new RangeMetricSummary(
                last.value(), average, max, availability, reason, series.size(), samples.size());
    }

    protected abstract boolean matchesType(Target target);

    protected MetricsCredentials resolveCredentials(Target target) {
        ObservabilityTargetConfiguration obs = target.observability();
        if (obs == null || obs.credentialRef() == null || obs.credentialRef().isBlank()) {
            return MetricsCredentials.none();
        }
        try {
            MetricsCredentials credentials = credentialProvider.getMetricsCredentials(obs.credentialRef());
            return validConfiguredCredentials(credentials) ? credentials : null;
        } catch (RuntimeException e) {
            log.debug("Configured metrics credentials unavailable");
            return null;
        }
    }

    private static boolean validConfiguredCredentials(MetricsCredentials credentials) {
        if (credentials == null) return false;
        if (credentials.hasBearer()) {
            return credentials.bearerToken().chars().allMatch(c -> c > 32 && c < 127);
        }
        return credentials.hasBasic() && credentials.username().indexOf(':') < 0
                && credentials.username().chars().noneMatch(c -> c < 32 || c == 127)
                && credentials.password().chars().noneMatch(c -> c < 32 || c == 127);
    }

    private SemanticMetricResult unauthorized(Target target, SemanticMetric metric, MetricWindow window) {
        return SemanticMetricResult.notAvailable(target.id().value(), metric, window, sourceName, "Unauthorized");
    }

    private SemanticMetricResult aborted(Target target, SemanticMetric metric, MetricWindow window, MetricsOperationBudget budget) {
        return SemanticMetricResult.notAvailable(target == null ? null : target.id().value(), metric, window, sourceName, budget.reason());
    }

    private SemanticMetricResult toResult(
            String targetId, SemanticMetric metric, MetricWindow window, PrometheusApiClient.Response response) {
        MetricsCatalog.Entry entry = metric == null ? null : MetricsCatalog.entry(metric);
        String unit = entry == null ? null : entry.unit();
        if (response == null || response.status() == null) {
            return SemanticMetricResult.notAvailable(targetId, metric, window, sourceName, SemanticMetricResult.REASON_INVALID_SAMPLE);
        }
        return switch (response.status()) {
            case OK -> {
                List<MetricSeries> series = response.series();
                if (series == null) {
                    yield SemanticMetricResult.notAvailable(targetId, metric, window, sourceName, SemanticMetricResult.REASON_INVALID_SAMPLE);
                }
                if (MetricsQueryBounds.exceedsSeriesLimit(series.size(), metricsConfig)) {
                    yield SemanticMetricResult.limitExceeded(
                            targetId, metric, window, sourceName, series.size());
                }
                String failure = MetricsTemporalValidation.instantFailure(series, Instant.now());
                if (failure != null) {
                    yield SemanticMetricResult.notAvailable(targetId, metric, window, sourceName, failure);
                }
                if (series.size() != 1) {
                    yield SemanticMetricResult.notAvailable(targetId, metric, window, sourceName, SemanticMetricResult.REASON_UNEXPECTED_SERIES);
                }
                MetricSample sample = series.getFirst().samples().getFirst();
                Double value = sample.value();
                Instant lastTs = sample.timestamp();
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
                    targetId, metric, window, sourceName, SemanticMetricResult.REASON_NO_SERIES);
            case INCOMPLETE -> SemanticMetricResult.notAvailable(
                    targetId, metric, window, sourceName,
                    SemanticMetricResult.REASON_INVALID_SAMPLE.equals(response.message())
                            ? SemanticMetricResult.REASON_INVALID_SAMPLE : SemanticMetricResult.REASON_TEMPORAL_COVERAGE);
            case UNAUTHORIZED -> SemanticMetricResult.notAvailable(
                    targetId, metric, window, sourceName, "Unauthorized");
            case FORBIDDEN -> SemanticMetricResult.notAvailable(
                    targetId, metric, window, sourceName, "Forbidden");
            case TIMEOUT -> SemanticMetricResult.notAvailable(
                    targetId, metric, window, sourceName, "Timed out");
            case OPERATION_ABORTED -> SemanticMetricResult.notAvailable(
                    targetId, metric, window, sourceName,
                    "OPERATION_INTERRUPTED".equals(response.message()) ? "OPERATION_INTERRUPTED" : "OPERATION_BUDGET_EXCEEDED");
            case RATE_LIMITED -> SemanticMetricResult.notAvailable(
                    targetId, metric, window, sourceName, "Rate limited");
            case NOT_FOUND -> SemanticMetricResult.notAvailable(
                    targetId, metric, window, sourceName, "Endpoint not found");
            case MALFORMED -> SemanticMetricResult.notAvailable(
                    targetId, metric, window, sourceName, "Malformed response");
            case LIMIT_EXCEEDED -> SemanticMetricResult.notAvailable(
                    targetId, metric, window, sourceName, SemanticMetricResult.REASON_SERIES_LIMIT);
            case SERVER_ERROR, NETWORK_ERROR -> SemanticMetricResult.notAvailable(
                    targetId, metric, window, sourceName, "Metrics backend request failed");
        };
    }

    private static MetricsProviderStatus mapStatus(PrometheusApiClient.Status status) {
        return switch (status) {
            case OK, EMPTY -> MetricsProviderStatus.AVAILABLE;
            case UNAUTHORIZED, FORBIDDEN -> MetricsProviderStatus.UNAUTHORIZED;
            case TIMEOUT, OPERATION_ABORTED, RATE_LIMITED, SERVER_ERROR, NETWORK_ERROR, MALFORMED, LIMIT_EXCEEDED, INCOMPLETE -> MetricsProviderStatus.DEGRADED;
            case NOT_FOUND -> MetricsProviderStatus.UNAVAILABLE;
        };
    }

    private Duration connectTimeout() {
        return Duration.ofMillis(Math.max(100, metricsConfig.connectTimeoutMs()));
    }

    private Duration readTimeout() {
        return Duration.ofMillis(Math.max(100, metricsConfig.readTimeoutMs()));
    }
}
