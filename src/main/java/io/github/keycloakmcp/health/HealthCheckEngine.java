package io.github.keycloakmcp.health;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Runs all registered {@link HealthCheck} beans and computes an overall status.
 * Severity order: CRITICAL &gt; WARNING &gt; UNKNOWN &gt; HEALTHY.
 * UNKNOWN alone does not elevate overall to CRITICAL.
 */
@ApplicationScoped
public class HealthCheckEngine {

    private static final Logger LOG = Logger.getLogger(HealthCheckEngine.class);

    private final Instance<HealthCheck> checks;

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "collection.operation-timeout-ms", defaultValue = "30000")
    long collectionTimeoutMs = CollectionBudget.DEFAULT_TIMEOUT_MS;

    @Inject
    public HealthCheckEngine(Instance<HealthCheck> checks) {
        this.checks = checks;
    }

    public HealthRunResult run(Target target) {
        try (var scope = CollectionBudget.open(target.id().value(), collectionTimeoutMs)) {
            return runWithinBudget(target, scope.budget());
        }
    }

    private HealthRunResult runWithinBudget(Target target, CollectionBudget budget) {
        Instant started = Instant.now();
        List<HealthComponentResult> results = new ArrayList<>();
        for (HealthCheck check : checks) {
            if (check == null) {
                continue;
            }
            long t0 = System.currentTimeMillis();
            try {
                budget.checkpoint();
                HealthComponentResult result = check.check(target);
                if (budget.exhausted() && !isBudgetPartial(result)) {
                    results.add(aborted(check.name(), budget.reason(), t0));
                } else if (result == null) {
                    results.add(HealthComponentResult.of(
                            check.name(),
                            HealthStatus.UNKNOWN,
                            "Check returned null",
                            Map.of("reasonCode", "CHECK_RESULT_UNAVAILABLE"),
                            System.currentTimeMillis() - t0));
                } else {
                    results.add(result);
                }
            } catch (CollectionBudget.Aborted e) {
                results.add(aborted(check.name(), e.reason(), t0));
            } catch (RuntimeException e) {
                if (budget.exhausted()) {
                    results.add(aborted(check.name(), budget.reason(), t0));
                    continue;
                }
                // Provider exception text and causes can contain credentials or response bodies.
                LOG.warnf("Health check %s failed for target=%s", check.name(), target.id().value());
                results.add(HealthComponentResult.of(
                        check.name(),
                        HealthStatus.UNKNOWN,
                        "Health check could not determine availability",
                        Map.of("reasonCode", "CHECK_FAILED"),
                        System.currentTimeMillis() - t0));
            }
        }
        HealthStatus overall = computeOverall(results);
        Map<String, String> componentStatuses = new LinkedHashMap<>();
        for (HealthComponentResult r : results) {
            componentStatuses.put(r.name(), r.status() == null ? HealthStatus.UNKNOWN.name() : r.status().name());
        }
        return new HealthRunResult(overall, List.copyOf(results), Map.copyOf(componentStatuses), started, Instant.now());
    }

    private static boolean isBudgetPartial(HealthComponentResult result) {
        if (result == null || result.details() == null) return false;
        Object reason = result.details().get("reasonCode");
        return Boolean.FALSE.equals(result.details().get("collectionComplete"))
                && (CollectionBudget.REASON_EXCEEDED.equals(reason) || CollectionBudget.REASON_INTERRUPTED.equals(reason));
    }

    private static HealthComponentResult aborted(String name, String reason, long started) {
        return HealthComponentResult.of(name, HealthStatus.UNKNOWN, "Health collection did not complete",
                Map.of("collectionComplete", false, "reasonCode", reason), System.currentTimeMillis() - started);
    }

    static HealthStatus computeOverall(List<HealthComponentResult> results) {
        if (results == null || results.isEmpty()) {
            return HealthStatus.UNKNOWN;
        }
        HealthStatus worst = HealthStatus.HEALTHY;
        for (HealthComponentResult result : results) {
            HealthStatus status = result == null || result.status() == null ? HealthStatus.UNKNOWN : result.status();
            if (status == HealthStatus.CRITICAL) {
                return HealthStatus.CRITICAL;
            }
            if (status == HealthStatus.WARNING) {
                worst = HealthStatus.WARNING;
            } else if (status == HealthStatus.UNKNOWN) {
                if (worst == HealthStatus.HEALTHY) {
                    worst = HealthStatus.UNKNOWN;
                }
            }
        }
        return worst;
    }

    public record HealthRunResult(
            HealthStatus overallStatus,
            List<HealthComponentResult> results,
            Map<String, String> componentStatuses,
            Instant startedAt,
            Instant completedAt) {
    }
}
