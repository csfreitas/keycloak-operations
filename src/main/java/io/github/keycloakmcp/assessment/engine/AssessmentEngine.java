package io.github.keycloakmcp.assessment.engine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.assessment.profile.AssessmentProfile;
import io.github.keycloakmcp.assessment.profile.ProfileRegistry;
import io.github.keycloakmcp.assessment.scoring.AssessmentScoring;
import io.github.keycloakmcp.collector.EvidenceCollector;
import io.github.keycloakmcp.config.AssessmentConfig;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AssessmentEngine {

    private static final Logger LOG = Logger.getLogger(AssessmentEngine.class);

    private final ProfileRegistry profileRegistry;
    private final YamlRuleLoader yamlRuleLoader;
    private final RuleEngine ruleEngine;
    private final AssessmentScoring scoring;
    private final AssessmentConfig assessmentConfig;
    private final McpMetrics metrics;

    @Inject
    public AssessmentEngine(
            ProfileRegistry profileRegistry,
            YamlRuleLoader yamlRuleLoader,
            RuleEngine ruleEngine,
            AssessmentScoring scoring,
            AssessmentConfig assessmentConfig,
            McpMetrics metrics) {
        this.profileRegistry = profileRegistry;
        this.yamlRuleLoader = yamlRuleLoader;
        this.ruleEngine = ruleEngine;
        this.scoring = scoring;
        this.assessmentConfig = assessmentConfig;
        this.metrics = metrics;
    }

    /**
     * Runs an assessment for the given target, stamping {@code targetId} on evidence and findings.
     */
    public AssessmentResult assess(Target target, String profileName, List<EvidenceCollector> collectors) {
        if (target == null) {
            throw McpException.invalidArgument("target must not be null");
        }
        Instant startedAt = Instant.now();
        String targetId = target.id().value();
        String resolvedProfile = (profileName == null || profileName.isBlank())
                ? assessmentConfig.defaultProfile()
                : profileName;

        AssessmentProfile profile;
        try {
            profile = profileRegistry.require(resolvedProfile);
        } catch (IllegalArgumentException e) {
            throw McpException.assessmentFailed(e.getMessage());
        }

        List<Evidence> evidence = collectEvidence(target, collectors);
        EvidenceContext context = new EvidenceContext(targetId, evidence);
        List<Rule> rules = yamlRuleLoader.loadForProfile(profile);
        LOG.debugf("Running assessment target=%s profile=%s rulePacks=%s rules=%d evidence=%d",
                targetId, profile.name(), profile.rulePackIds(), rules.size(), evidence.size());

        List<Finding> findings = ruleEngine.evaluate(rules, context).stream()
                .map(f -> stampTargetId(targetId, f))
                .toList();
        int score = scoring.score(findings);
        metrics.recordAssessmentRun(findings.size());

        return new AssessmentResult(
                null,
                targetId,
                profile.name(),
                score,
                findings,
                evidence,
                startedAt,
                Instant.now());
    }

    /**
     * @deprecated Prefer {@link #assess(Target, String, List)} so findings carry a targetId.
     */
    @Deprecated
    public AssessmentResult run(String profileName, List<EvidenceCollector> collectors) {
        Instant startedAt = Instant.now();
        String resolvedProfile = (profileName == null || profileName.isBlank())
                ? assessmentConfig.defaultProfile()
                : profileName;

        AssessmentProfile profile;
        try {
            profile = profileRegistry.require(resolvedProfile);
        } catch (IllegalArgumentException e) {
            throw McpException.assessmentFailed(e.getMessage());
        }

        List<Evidence> evidence = collectEvidence(null, collectors);
        EvidenceContext context = new EvidenceContext(evidence);
        List<Rule> rules = yamlRuleLoader.loadBuiltInAndClasspathRules();
        LOG.debugf("Running assessment profile=%s rulePacks=%s rules=%d evidence=%d",
                profile.name(), profile.rulePackIds(), rules.size(), evidence.size());

        List<Finding> findings = ruleEngine.evaluate(rules, context);
        int score = scoring.score(findings);
        metrics.recordAssessmentRun(findings.size());

        return new AssessmentResult(
                null,
                context.targetId(),
                profile.name(),
                score,
                findings,
                evidence,
                startedAt,
                Instant.now());
    }

    private List<Evidence> collectEvidence(Target target, List<EvidenceCollector> collectors) {
        if (collectors == null || collectors.isEmpty()) {
            return List.of();
        }
        List<Evidence> evidence = new ArrayList<>();
        for (EvidenceCollector collector : collectors) {
            if (collector == null) {
                continue;
            }
            try {
                List<Evidence> collected = target == null
                        ? List.of()
                        : collector.collect(target);
                if (collected != null) {
                    evidence.addAll(collected);
                }
            } catch (RuntimeException e) {
                throw McpException.evidenceCollectionFailed(
                        "Evidence collection failed for source " + collector.source(), e);
            }
        }
        return List.copyOf(evidence);
    }

    private static Finding stampTargetId(String targetId, Finding finding) {
        if (finding.targetId() != null && !finding.targetId().isBlank() && !"-".equals(finding.targetId())) {
            return finding;
        }
        return new Finding(
                targetId,
                finding.id(),
                finding.title(),
                finding.category(),
                finding.severity(),
                finding.status(),
                finding.description(),
                finding.evidence(),
                finding.impact(),
                finding.recommendation(),
                finding.references());
    }
}
