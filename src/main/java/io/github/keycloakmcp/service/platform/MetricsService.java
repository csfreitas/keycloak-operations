package io.github.keycloakmcp.service.platform;

import io.github.keycloakmcp.observability.metrics.MetricsProvider;
import io.github.keycloakmcp.observability.metrics.MetricsQuery;
import io.github.keycloakmcp.observability.metrics.MetricsResult;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Semantic metrics facade. Builds fixed internal queries — never accepts raw PromQL from callers.
 */
@ApplicationScoped
public class MetricsService {

    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final MetricsProvider metricsProvider;

    @Inject
    public MetricsService(
            TargetResolver targetResolver,
            TargetAuthorizationService targetAuthorization,
            MetricsProvider metricsProvider) {
        this.targetResolver = targetResolver;
        this.targetAuthorization = targetAuthorization;
        this.metricsProvider = metricsProvider;
    }

    public MetricsResult requests(String targetId) {
        return query(targetId, MetricsQuery.Semantic.REQUESTS);
    }

    public MetricsResult latency(String targetId) {
        return query(targetId, MetricsQuery.Semantic.LATENCY);
    }

    public MetricsResult jvm(String targetId) {
        return query(targetId, MetricsQuery.Semantic.JVM);
    }

    public MetricsResult databasePool(String targetId) {
        return query(targetId, MetricsQuery.Semantic.DATABASE_POOL);
    }

    public MetricsResult resources(String targetId) {
        return query(targetId, MetricsQuery.Semantic.RESOURCES);
    }

    private MetricsResult query(String targetId, MetricsQuery.Semantic semantic) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        return metricsProvider.query(new MetricsQuery(targetId, semantic));
    }
}
