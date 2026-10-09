package io.github.keycloakmcp.observability.metrics;

import java.util.ArrayList;
import java.util.List;

import io.github.keycloakmcp.target.Target;

/**
 * Target-scoped metrics backend. Callers never supply PromQL — only semantics.
 */
public interface MetricsProvider {

    MetricsProviderStatus status(Target target);

    /** Compatibility guard; built-in remote providers also apply the budget to transport. */
    default MetricsProviderStatus status(Target target, MetricsOperationBudget budget) {
        if (budget.exhausted()) return MetricsProviderStatus.DEGRADED;
        MetricsProviderStatus result = status(target);
        return budget.exhausted() ? MetricsProviderStatus.DEGRADED : result;
    }

    SemanticMetricResult query(Target target, SemanticMetric metric, MetricWindow window);

    default SemanticMetricResult query(Target target, SemanticMetric metric, MetricWindow window,
            MetricsOperationBudget budget) {
        if (budget.exhausted()) return aborted(target, metric, window, budget);
        SemanticMetricResult result = query(target, metric, window);
        return budget.exhausted() ? aborted(target, metric, window, budget) : result;
    }

    List<SemanticMetricResult> queryCategory(Target target, MetricCategory category, MetricWindow window);

    default List<SemanticMetricResult> queryCategory(Target target, MetricCategory category, MetricWindow window,
            MetricsOperationBudget budget) {
        List<SemanticMetricResult> results = new ArrayList<>();
        for (SemanticMetric metric : MetricsCatalog.forCategory(category)) {
            results.add(budget.exhausted() ? aborted(target, metric, window, budget)
                    : query(target, metric, window, budget));
        }
        return List.copyOf(results);
    }

    boolean supported(Target target);

    /**
     * Controlled series-presence probe (count of a known metric family). Never accepts caller PromQL.
     */
    default SemanticMetricResult probeSeries(Target target, String controlledMetricFamily) {
        return SemanticMetricResult.notAvailable(
                target == null ? null : target.id().value(), null, MetricWindow.defaultWindow(),
                null, "Series probe unsupported");
    }

    default SemanticMetricResult probeSeries(Target target, String controlledMetricFamily, MetricsOperationBudget budget) {
        if (budget.exhausted()) return aborted(target, null, MetricWindow.defaultWindow(), budget);
        SemanticMetricResult result = probeSeries(target, controlledMetricFamily);
        return budget.exhausted() ? aborted(target, null, MetricWindow.defaultWindow(), budget) : result;
    }

    /** Validates actual binary up values; series presence alone is never scrape success. */
    default ScrapeObservation probeScrape(Target target) {
        return ScrapeObservation.unavailable("SCRAPE_PROBE_UNSUPPORTED");
    }

    default ScrapeObservation probeScrape(Target target, MetricsOperationBudget budget) {
        if (budget.exhausted()) return ScrapeObservation.unavailable(budget.reason());
        ScrapeObservation result = probeScrape(target);
        return budget.exhausted() ? ScrapeObservation.unavailable(budget.reason()) : result;
    }

    /**
     * Range analysis for metrics that benefit from temporal aggregates (e.g. DB awaiting).
     */
    default RangeMetricSummary queryRange(Target target, SemanticMetric metric, MetricWindow window) {
        return RangeMetricSummary.fromInstant(query(target, metric, window));
    }

    default RangeMetricSummary queryRange(Target target, SemanticMetric metric, MetricWindow window,
            MetricsOperationBudget budget) {
        if (budget.exhausted()) return RangeMetricSummary.notAvailable(budget.reason());
        RangeMetricSummary result = queryRange(target, metric, window);
        return budget.exhausted() ? RangeMetricSummary.notAvailable(budget.reason()) : result;
    }

    private static SemanticMetricResult aborted(Target target, SemanticMetric metric, MetricWindow window,
            MetricsOperationBudget budget) {
        return SemanticMetricResult.notAvailable(target == null ? null : target.id().value(), metric, window,
                null, budget.reason());
    }
}
