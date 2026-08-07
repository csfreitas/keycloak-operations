package io.github.keycloakmcp.assessment.engine;

import java.time.Instant;
import java.util.List;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.assessment.profile.AssessmentProfile;
import io.github.keycloakmcp.assessment.profile.AssessmentProfileResolver;
import io.github.keycloakmcp.assessment.profile.ProfileRegistry;
import io.github.keycloakmcp.assessment.scoring.AssessmentScoring;
import io.github.keycloakmcp.collector.AssessmentEvidenceService;
import io.github.keycloakmcp.collector.AssessmentEvidenceService.EvidenceCollectionResult;
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
    private final AssessmentProfileResolver profileResolver;
    private final YamlRuleLoader yamlRuleLoader;
    private final RuleEngine ruleEngine;
    private final AssessmentScoring scoring;
    private final AssessmentConfig assessmentConfig;
    private final AssessmentEvidenceService evidenceService;
    private final McpMetrics metrics;

    @Inject
    public AssessmentEngine(
            ProfileRegistry profileRegistry,
            AssessmentProfileResolver profileResolver,
            YamlRuleLoader yamlRuleLoader,
            RuleEngine ruleEngine,
            AssessmentScoring scoring,
            AssessmentConfig assessmentConfig,
            AssessmentEvidenceService evidenceService,
            McpMetrics metrics) {
        this.profileRegistry = profileRegistry;
        this.profileResolver = profileResolver;
        this.yamlRuleLoader = yamlRuleLoader;
        this.ruleEngine = ruleEngine;
        this.scoring = scoring;
        this.assessmentConfig = assessmentConfig;
        this.evidenceService = evidenceService;
        this.metrics = metrics;
    }

    /**
     * Runs an assessment for the given target using {@link AssessmentEvidenceService}.
     * Blank/null profile → suggested via {@link AssessmentProfileResolver}, else default-profile.
     */
    public AssessmentResult assess(Target target, String profileName) {
        if (target == null) {
            throw McpException.invalidArgument("target must not be null");
        }
        Instant startedAt = Instant.now();
        EvidenceCollectionResult collection = evidenceService.collect(target);
        String resolvedProfile = resolveProfile(profileName, target, collection.evidence());
        AssessmentProfile profile = requireProfile(resolvedProfile);
        return buildResult(target, profile, collection, startedAt);
    }

    /**
     * Test/helper overload that uses explicit collectors instead of {@link AssessmentEvidenceService}.
     *
     * @deprecated Prefer {@link #assess(Target, String)}.
     */
    @Deprecated
    public AssessmentResult assess(Target target, String profileName, List<EvidenceCollector> collectors) {
        if (target == null) {
            throw McpException.invalidArgument("target must not be null");
        }
        Instant startedAt = Instant.now();
        List<Evidence> evidence = collectEvidenceLegacy(target, collectors);
        List<String> collected = collectors == null
                ? List.of()
                : collectors.stream().map(EvidenceCollector::source).toList();
        EvidenceCollectionResult collection = new EvidenceCollectionResult(evidence, collected, List.of());
        String resolvedProfile = resolveProfile(profileName, target, evidence);
        AssessmentProfile profile = requireProfile(resolvedProfile);
        return buildResult(target, profile, collection, startedAt);
    }

    /**
     * @deprecated Prefer {@link #assess(Target, String)}.
     */
    @Deprecated
    public AssessmentResult run(String profileName, List<EvidenceCollector> collectors) {
        Instant startedAt = Instant.now();
        String resolvedProfile = (profileName == null || profileName.isBlank())
                ? assessmentConfig.defaultProfile()
                : profileName;
        AssessmentProfile profile = requireProfile(resolvedProfile);

        List<Evidence> evidence = collectEvidenceLegacy(null, collectors);
        EvidenceContext context = new EvidenceContext(evidence);
        List<Rule> rules = yamlRuleLoader.loadBuiltInAndClasspathRules();
        RuleEvaluationResult evaluation = ruleEngine.evaluateDetailed(rules, context);
        int score = scoring.score(evaluation.findings());
        metrics.recordAssessmentRun(evaluation.findings().size());

        return new AssessmentResult(
                null,
                context.targetId(),
                profile.name(),
                AssessmentScope.target(context.targetId()),
                AssessmentStatus.COMPLETE,
                score,
                scoring.categoryScores(evaluation.findings()),
                100,
                AssessmentConfidence.MEDIUM,
                evaluation.rulesEvaluated(),
                evaluation.rulesMatched(),
                evaluation.rulesSkipped(),
                evaluation.rulesNotEvaluated(),
                evaluation.missingEvidence(),
                evaluation.findings(),
                evidence,
                startedAt,
                Instant.now());
    }

    private AssessmentResult buildResult(
            Target target,
            AssessmentProfile profile,
            EvidenceCollectionResult collection,
            Instant startedAt) {
        String targetId = target.id().value();
        EvidenceContext context = new EvidenceContext(targetId, collection.evidence());
        List<Rule> rules = yamlRuleLoader.loadForProfile(profile);
        LOG.debugf(
                "Running assessment target=%s profile=%s rulePacks=%s rules=%d evidence=%d failedSources=%s",
                targetId,
                profile.name(),
                profile.rulePackIds(),
                rules.size(),
                collection.evidence().size(),
                collection.failedSources());

        RuleEvaluationResult evaluation = ruleEngine.evaluateDetailed(rules, context);
        List<Finding> findings = evaluation.findings().stream()
                .map(f -> stampTargetId(targetId, f))
                .toList();

        int overallScore = scoring.score(findings);
        var categoryScores = scoring.categoryScores(findings);
        int completeness = computeCompleteness(target, profile, collection, evaluation.rulesNotEvaluated());
        AssessmentConfidence confidence = computeConfidence(target, profile, collection);
        AssessmentStatus status = computeStatus(profile, collection, evaluation.rulesNotEvaluated());

        metrics.recordAssessmentRun(findings.size());

        return new AssessmentResult(
                null,
                targetId,
                profile.name(),
                AssessmentScope.target(targetId),
                status,
                overallScore,
                categoryScores,
                completeness,
                confidence,
                evaluation.rulesEvaluated(),
                evaluation.rulesMatched(),
                evaluation.rulesSkipped(),
                evaluation.rulesNotEvaluated(),
                evaluation.missingEvidence(),
                findings,
                collection.evidence(),
                startedAt,
                Instant.now());
    }

    private String resolveProfile(String profileName, Target target, List<Evidence> evidence) {
        if (profileName != null && !profileName.isBlank()) {
            return profileName;
        }
        return profileResolver.suggest(target, evidence).orElse(assessmentConfig.defaultProfile());
    }

    private static int computeCompleteness(
            Target target,
            AssessmentProfile profile,
            EvidenceCollectionResult collection,
            int rulesNotEvaluated) {
        // Metrics are OPTIONAL unless the profile lists them in requiredEvidenceSources.
        List<String> requiredSources = profile.requiredEvidenceSources().isEmpty()
                ? defaultRequiredSources(target)
                : profile.requiredEvidenceSources();
        int required = 0;
        int collected = 0;
        for (String source : requiredSources) {
            if ("metrics".equals(source) && !profileRequiresMetrics(profile) && !target.hasMetrics()) {
                continue; // treat as optional when not required by profile
            }
            required++;
            if (collection.collectedSources().contains(source)
                    && !collection.failedSources().contains(source)) {
                collected++;
            }
        }
        int sourceCompleteness = required == 0 ? 100 : (int) Math.round((collected * 100.0) / required);
        int adjusted = sourceCompleteness - (rulesNotEvaluated * 2);
        return Math.max(0, Math.min(100, adjusted));
    }

    private static List<String> defaultRequiredSources(Target target) {
        java.util.ArrayList<String> sources = new java.util.ArrayList<>();
        sources.add("keycloak");
        if (target.hasInfrastructure()) {
            sources.add("infrastructure");
        }
        // metrics intentionally omitted from defaults — optional
        return List.copyOf(sources);
    }

    private static boolean profileRequiresMetrics(AssessmentProfile profile) {
        return profile.requiredEvidenceSources().contains("metrics");
    }

    private static AssessmentConfidence computeConfidence(
            Target target, AssessmentProfile profile, EvidenceCollectionResult collection) {
        boolean keycloakOk = collection.collectedSources().contains("keycloak")
                && !collection.failedSources().contains("keycloak");
        if (!keycloakOk) {
            return AssessmentConfidence.LOW;
        }
        if (target.hasInfrastructure()) {
            boolean infraOk = collection.collectedSources().contains("infrastructure")
                    && !collection.failedSources().contains("infrastructure");
            if (!infraOk) {
                return AssessmentConfidence.MEDIUM;
            }
        }
        if (profileRequiresMetrics(profile)) {
            boolean metricsOk = collection.collectedSources().contains("metrics")
                    && !collection.failedSources().contains("metrics");
            return metricsOk ? AssessmentConfidence.HIGH : AssessmentConfidence.MEDIUM;
        }
        // Metrics failure alone does not downgrade confidence when optional
        if (target.hasInfrastructure()) {
            return AssessmentConfidence.HIGH;
        }
        return AssessmentConfidence.MEDIUM;
    }

    private static AssessmentStatus computeStatus(
            AssessmentProfile profile, EvidenceCollectionResult collection, int rulesNotEvaluated) {
        if (collection.failedSources().contains("keycloak")) {
            return AssessmentStatus.FAILED;
        }
        // Metrics is optional unless required by profile — filter optional failures
        List<String> materialFailures = collection.failedSources().stream()
                .filter(s -> !"metrics".equals(s) || profileRequiresMetrics(profile))
                .toList();
        if (!materialFailures.isEmpty() || rulesNotEvaluated > 0) {
            return AssessmentStatus.PARTIAL;
        }
        if (profileRequiresMetrics(profile) && !collection.collectedSources().contains("metrics")) {
            return AssessmentStatus.PARTIAL;
        }
        return AssessmentStatus.COMPLETE;
    }

    private AssessmentProfile requireProfile(String resolvedProfile) {
        try {
            return profileRegistry.require(resolvedProfile);
        } catch (IllegalArgumentException e) {
            throw McpException.assessmentFailed(e.getMessage());
        }
    }

    private List<Evidence> collectEvidenceLegacy(Target target, List<EvidenceCollector> collectors) {
        if (collectors == null || collectors.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<Evidence> evidence = new java.util.ArrayList<>();
        for (EvidenceCollector collector : collectors) {
            if (collector == null) {
                continue;
            }
            try {
                List<Evidence> collected = target == null ? List.of() : collector.collect(target);
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
        return finding.withTargetId(targetId);
    }
}
