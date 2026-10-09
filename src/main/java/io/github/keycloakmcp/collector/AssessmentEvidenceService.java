package io.github.keycloakmcp.collector;

import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.collection.CollectionBudget;
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

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "collection.operation-timeout-ms", defaultValue = "30000")
    long collectionTimeoutMs = CollectionBudget.DEFAULT_TIMEOUT_MS;

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
        try (var scope = CollectionBudget.open(target.id().value(), collectionTimeoutMs)) {
            return collectWithinBudget(target);
        }
    }

    private EvidenceCollectionResult collectWithinBudget(Target target) {
        List<Evidence> evidence = new ArrayList<>();
        List<String> failedSources = new ArrayList<>();
        List<String> collectedSources = new ArrayList<>();
        List<String> partialSources = new ArrayList<>();

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

        for (String source : List.of("keycloak", "infrastructure", "metrics")) {
            if (evidence.stream().anyMatch(e -> source.equals(e.source())
                    && target.id().value().equals(e.targetId())
                    && (source + ".collection.complete").equals(e.key())
                    && !Boolean.TRUE.equals(e.value()))) {
                partialSources.add(source);
            }
        }
        return new EvidenceCollectionResult(
                List.copyOf(evidence),
                List.copyOf(collectedSources),
                List.copyOf(failedSources),
                List.copyOf(partialSources));
    }

    private void collectSource(
            Target target,
            EvidenceCollector collector,
            List<Evidence> evidence,
            List<String> collectedSources,
            List<String> failedSources) {
        try {
            CollectionBudget.checkpoint(target.id().value());
            List<Evidence> collected = collector.collect(target);
            if (collected == null || collected.isEmpty()) {
                failedSources.add(collector.source());
                return;
            }
            // Only a collector's explicit partial contract may retain its validated prefix
            // after expiry. A late, unmarked successful response is not trustworthy completion.
            if (CollectionBudget.current().exhausted() && collected.stream().noneMatch(e ->
                    target.id().value().equals(e.targetId()) && collector.source().equals(e.source())
                    && (collector.source() + ".collection.complete").equals(e.key())
                    && Boolean.FALSE.equals(e.value()))) {
                failedSources.add(collector.source());
                return;
            }
            evidence.addAll(collected);
            collectedSources.add(collector.source());
        } catch (RuntimeException e) {
            LOG.warnf("Evidence collection failed for source=%s target=%s",
                    collector.source(), target.id().value());
            failedSources.add(collector.source());
        }
    }

    public record EvidenceCollectionResult(
            List<Evidence> evidence,
            List<String> collectedSources,
            List<String> failedSources,
            List<String> partialSources) {
        public EvidenceCollectionResult(List<Evidence> evidence, List<String> collectedSources,
                List<String> failedSources) {
            this(evidence, collectedSources, failedSources, List.of());
        }
    }
}
