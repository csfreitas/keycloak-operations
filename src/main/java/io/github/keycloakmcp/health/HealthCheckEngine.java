package io.github.keycloakmcp.health;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.domain.platform.HealthStatus;
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

    @Inject
    public HealthCheckEngine(Instance<HealthCheck> checks) {
        this.checks = checks;
    }

    public HealthRunResult run(Target target) {
        Instant started = Instant.now();
        List<HealthComponentResult> results = new ArrayList<>();
        for (HealthCheck check : checks) {
            if (check == null) {
                continue;
            }
            long t0 = System.currentTimeMillis();
            try {
                HealthComponentResult result = check.check(target);
                if (result == null) {
                    results.add(HealthComponentResult.of(
                            check.name(),
                            HealthStatus.UNKNOWN,
                            "Check returned null",
                            Map.of(),
                            System.currentTimeMillis() - t0));
                } else {
                    results.add(result);
                }
            } catch (RuntimeException e) {
                LOG.warnf(e, "Health check %s failed for target=%s", check.name(), target.id().value());
                results.add(HealthComponentResult.of(
                        check.name(),
                        HealthStatus.CRITICAL,
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                        Map.of(),
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

    static HealthStatus computeOverall(List<HealthComponentResult> results) {
        if (results == null || results.isEmpty()) {
            return HealthStatus.UNKNOWN;
        }
        HealthStatus worst = HealthStatus.HEALTHY;
        boolean anyKnownHealthyOrWorse = false;
        for (HealthComponentResult result : results) {
            HealthStatus status = result.status() == null ? HealthStatus.UNKNOWN : result.status();
            if (status == HealthStatus.CRITICAL) {
                return HealthStatus.CRITICAL;
            }
            if (status == HealthStatus.WARNING) {
                worst = HealthStatus.WARNING;
                anyKnownHealthyOrWorse = true;
            } else if (status == HealthStatus.HEALTHY) {
                anyKnownHealthyOrWorse = true;
                if (worst == HealthStatus.HEALTHY) {
                    // keep HEALTHY
                }
            } else if (status == HealthStatus.UNKNOWN) {
                // UNKNOWN never elevates above current non-UNKNOWN worst
                if (!anyKnownHealthyOrWorse && worst == HealthStatus.HEALTHY) {
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
