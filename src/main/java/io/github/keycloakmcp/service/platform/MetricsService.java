package io.github.keycloakmcp.service.platform;

import java.util.List;

import io.github.keycloakmcp.domain.metrics.MetricsStatusView;
import io.github.keycloakmcp.domain.metrics.PerformanceSummary;
import io.github.keycloakmcp.observability.metrics.MetricCategory;
import io.github.keycloakmcp.observability.metrics.MetricWindow;
import io.github.keycloakmcp.observability.metrics.MetricsProvider;
import io.github.keycloakmcp.observability.metrics.MetricsProviderFactory;
import io.github.keycloakmcp.observability.metrics.MetricsProviderStatus;
import io.github.keycloakmcp.observability.metrics.SemanticMetricResult;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Semantic metrics facade. Never accepts raw PromQL from callers.
 */
@ApplicationScoped
public class MetricsService {

    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final MetricsProviderFactory metricsProviderFactory;
    private final PerformanceSummaryService performanceSummaryService;

    @Inject
    public MetricsService(
            TargetResolver targetResolver,
            TargetAuthorizationService targetAuthorization,
            MetricsProviderFactory metricsProviderFactory,
            PerformanceSummaryService performanceSummaryService) {
        this.targetResolver = targetResolver;
        this.targetAuthorization = targetAuthorization;
        this.metricsProviderFactory = metricsProviderFactory;
        this.performanceSummaryService = performanceSummaryService;
    }

    public MetricsStatusView status(String targetId) {
        Target target = requireReadable(targetId);
        MetricsProvider provider = metricsProviderFactory.forTarget(target);
        MetricsProviderStatus status = provider.status(target);
        String metricsType = target.observability() == null ? null : target.observability().metricsType();
        boolean configured = target.hasMetrics() || provider.supported(target);
        String message = switch (status) {
            case AVAILABLE -> "Metrics backend reachable";
            case DEGRADED -> "Metrics backend degraded";
            case UNAUTHORIZED -> "Metrics backend unauthorized";
            case UNAVAILABLE -> "Metrics backend unavailable";
            case NOT_CONFIGURED -> "Metrics not configured for target";
            case UNKNOWN -> "Metrics status unknown";
        };
        return MetricsStatusView.of(targetId, status, metricsType, configured, message);
    }

    public PerformanceSummary summary(String targetId, String window) {
        Target target = requireReadable(targetId);
        MetricWindow w = parseWindowOrDefault(window, performanceSummaryService.interactiveWindow());
        return performanceSummaryService.summarize(target, w);
    }

    /**
     * Assessment path — uses assessment window; caller already holds a Target (no re-auth).
     */
    public PerformanceSummary summaryForAssessment(Target target) {
        return performanceSummaryService.summarize(target, performanceSummaryService.assessmentWindow());
    }

    public List<SemanticMetricResult> category(String targetId, MetricCategory category, String window) {
        Target target = requireReadable(targetId);
        MetricWindow w = parseWindowOrDefault(window, performanceSummaryService.interactiveWindow());
        return performanceSummaryService.category(target, category, w);
    }

    private Target requireReadable(String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        return target;
    }

    private static MetricWindow parseWindowOrDefault(String window, MetricWindow fallback) {
        if (window == null || window.isBlank()) {
            return fallback;
        }
        return MetricWindow.tryParse(window).orElse(fallback);
    }
}
