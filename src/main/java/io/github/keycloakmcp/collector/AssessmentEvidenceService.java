package io.github.keycloakmcp.collector;

import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.collector.infrastructure.InfrastructureEvidenceCollector;
import io.github.keycloakmcp.collector.keycloak.KeycloakEvidenceCollector;
import io.github.keycloakmcp.collector.metrics.MetricsEvidenceCollector;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Explicit evidence pipeline for assessments. Decides which collectors run;
 * does not talk to Fabric8 or Admin API directly.
 */
@ApplicationScoped
public class AssessmentEvidenceService {

    private static final Logger LOG = Logger.getLogger(AssessmentEvidenceService.class);

    private final KeycloakEvidenceCollector keycloakEvidenceCollector;
    private final InfrastructureEvidenceCollector infrastructureEvidenceCollector;
    private final MetricsEvidenceCollector metricsEvidenceCollector;

    @Inject
    public AssessmentEvidenceService(
            KeycloakEvidenceCollector keycloakEvidenceCollector,
            InfrastructureEvidenceCollector infrastructureEvidenceCollector,
            MetricsEvidenceCollector metricsEvidenceCollector) {
        this.keycloakEvidenceCollector = keycloakEvidenceCollector;
        this.infrastructureEvidenceCollector = infrastructureEvidenceCollector;
        this.metricsEvidenceCollector = metricsEvidenceCollector;
    }

    public EvidenceCollectionResult collect(Target target) {
        if (target == null) {
            throw McpException.invalidArgument("target must not be null");
        }
        List<Evidence> evidence = new ArrayList<>();
        List<String> failedSources = new ArrayList<>();
        List<String> collectedSources = new ArrayList<>();

        collectSource(target, keycloakEvidenceCollector, evidence, collectedSources, failedSources);
        if (target.hasInfrastructure()) {
            collectSource(target, infrastructureEvidenceCollector, evidence, collectedSources, failedSources);
        }
        if (target.hasObservabilityMetrics()
                || (target.observability() != null && target.observability().hasMetrics())) {
            // Metrics are optional for overall assessment success; failures → failedSources only.
            collectSource(target, metricsEvidenceCollector, evidence, collectedSources, failedSources);
        }

        // Target environment as evidence for appliesWhen
        evidence.add(new Evidence(
                target.id().value(),
                "target",
                "target",
                "target.environment",
                target.environment().name(),
                java.time.Instant.now()));
        collectedSources.add("target");

        return new EvidenceCollectionResult(
                List.copyOf(evidence),
                List.copyOf(collectedSources),
                List.copyOf(failedSources));
    }

    private void collectSource(
            Target target,
            EvidenceCollector collector,
            List<Evidence> evidence,
            List<String> collectedSources,
            List<String> failedSources) {
        try {
            List<Evidence> collected = collector.collect(target);
            if (collected != null) {
                evidence.addAll(collected);
            }
            collectedSources.add(collector.source());
        } catch (RuntimeException e) {
            LOG.warnf(e, "Evidence collection failed for source=%s target=%s",
                    collector.source(), target.id().value());
            failedSources.add(collector.source());
        }
    }

    public record EvidenceCollectionResult(
            List<Evidence> evidence,
            List<String> collectedSources,
            List<String> failedSources) {
    }
}
