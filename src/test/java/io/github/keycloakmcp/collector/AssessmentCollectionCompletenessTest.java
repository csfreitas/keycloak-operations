package io.github.keycloakmcp.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.keycloakmcp.assessment.engine.*;
import io.github.keycloakmcp.assessment.profile.AssessmentProfile;
import io.github.keycloakmcp.assessment.profile.AssessmentProfileResolver;
import io.github.keycloakmcp.assessment.profile.ProfileRegistry;
import io.github.keycloakmcp.assessment.scoring.AssessmentScoring;
import io.github.keycloakmcp.collector.infrastructure.InfrastructureEvidenceCollector;
import io.github.keycloakmcp.collector.keycloak.KeycloakEvidenceCollector;
import io.github.keycloakmcp.collector.metrics.MetricsEvidenceCollector;
import io.github.keycloakmcp.config.TestAssessmentConfig;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.target.*;

class AssessmentCollectionCompletenessTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void expiredCollectionRejectsUnmarkedLateFactsAndDoesNotStartNextSource(boolean explicitlyPartial) {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var budget = new io.github.keycloakmcp.collection.CollectionBudget(java.time.Duration.ofSeconds(1), clock::get);
        Target target = target();
        var keycloak = mock(KeycloakEvidenceCollector.class);
        var infra = mock(InfrastructureEvidenceCollector.class);
        when(keycloak.source()).thenReturn("keycloak");
        when(infra.source()).thenReturn("infrastructure");
        when(keycloak.collect(target)).thenAnswer(inv -> {
            clock.set(1_000_000_000L);
            return List.of(marker("keycloak", !explicitlyPartial));
        });
        try (var scope = io.github.keycloakmcp.collection.CollectionBudget.open("lab-a", budget)) {
            var result = new AssessmentEvidenceService(keycloak, infra, mock(MetricsEvidenceCollector.class)).collect(target);
            assertThat(result.failedSources()).contains("infrastructure");
            org.mockito.Mockito.verify(infra, org.mockito.Mockito.never()).collect(target);
            if (explicitlyPartial) {
                assertThat(result.partialSources()).containsExactly("keycloak");
                assertThat(result.collectedSources()).contains("keycloak");
            } else {
                assertThat(result.failedSources()).contains("keycloak");
                assertThat(result.evidence()).noneMatch(e -> "keycloak".equals(e.source()));
            }
            assertThat(engine(result).assess(target, "test").scoreAvailable()).isFalse();
        }
        assertThat(io.github.keycloakmcp.collection.CollectionBudget.current()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"keycloak", "infrastructure"})
    void incompleteSourceSurvivesCollectionAndSuppressesAvailableScore(String source) {
        Target target = target();
        var collection = service(target, source, false).collect(target);
        assertThat(collection.partialSources()).containsExactly(source);
        assertThat(collection.collectedSources()).contains("keycloak", "infrastructure");
        assertThat(collection.failedSources()).isEmpty();
        var result = engine(collection).assess(target, "test");
        // All selected rules evaluated: the source marker itself must keep the result partial.
        assertThat(result.rulesNotEvaluated()).isZero();
        assertThat(result.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(result.evidenceCompleteness()).isEqualTo(99);
        assertThat(result.scoreAvailable()).isFalse();
        assertThat(result.confidence()).isEqualTo("keycloak".equals(source)
                ? AssessmentConfidence.LOW : AssessmentConfidence.MEDIUM);
    }

    @Test
    void explicitCompleteSourcesCanStillBeComplete() {
        Target target = target();
        var collection = service(target, "infrastructure", true).collect(target);
        assertThat(collection.partialSources()).isEmpty();
        var result = engine(collection).assess(target, "test");
        assertThat(result.status()).isEqualTo(AssessmentStatus.COMPLETE);
        assertThat(result.evidenceCompleteness()).isEqualTo(100);
        assertThat(result.confidence()).isEqualTo(AssessmentConfidence.HIGH);
        assertThat(result.scoreAvailable()).isTrue();
    }

    @Test
    void malformedCompleteMarkerIsNotTruthy() {
        Target target = target();
        var collection = service(target, "infrastructure", "true").collect(target);
        assertThat(collection.partialSources()).containsExactly("infrastructure");
        assertThat(engine(collection).assess(target, "test").scoreAvailable()).isFalse();
    }

    @Test
    void anyIncompleteMarkerWinsOverDuplicateSuccess() {
        Target target = target();
        var keycloak = mock(KeycloakEvidenceCollector.class);
        var infra = mock(InfrastructureEvidenceCollector.class);
        when(keycloak.source()).thenReturn("keycloak");
        when(infra.source()).thenReturn("infrastructure");
        when(keycloak.collect(target)).thenReturn(List.of(marker("keycloak", true)));
        when(infra.collect(target)).thenReturn(List.of(marker("infrastructure", true), marker("infrastructure", false)));
        var collection = new AssessmentEvidenceService(keycloak, infra, mock(MetricsEvidenceCollector.class)).collect(target);
        assertThat(collection.partialSources()).containsExactly("infrastructure");
    }

    @Test
    void sourceMarkerCannotLabelAnotherCollectorPartial() {
        Target target = target();
        var keycloak = mock(KeycloakEvidenceCollector.class);
        var infra = mock(InfrastructureEvidenceCollector.class);
        when(keycloak.source()).thenReturn("keycloak");
        when(infra.source()).thenReturn("infrastructure");
        when(keycloak.collect(target)).thenReturn(List.of(new Evidence("lab-a", "keycloak", "collection",
                "infrastructure.collection.complete", false, Instant.EPOCH)));
        when(infra.collect(target)).thenReturn(List.of(marker("infrastructure", true)));
        var collection = new AssessmentEvidenceService(keycloak, infra, mock(MetricsEvidenceCollector.class)).collect(target);
        assertThat(collection.partialSources()).isEmpty();
    }

    @Test
    void foreignEvidenceStillFailsBeforeRuleEvaluation() {
        var collection = new AssessmentEvidenceService.EvidenceCollectionResult(
                List.of(new Evidence("lab-b", "infrastructure", "collection", "infrastructure.collection.complete", true, Instant.EPOCH)),
                List.of("keycloak", "infrastructure"), List.of());
        assertThatThrownBy(() -> engine(collection).assess(target(), "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Evidence must belong to the assessed target");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void metricsFailureOnlyDowngradesWhenRequired(boolean required) {
        var collection = new AssessmentEvidenceService.EvidenceCollectionResult(
                List.of(marker("keycloak", true), marker("infrastructure", true)),
                List.of("keycloak", "infrastructure"), List.of("metrics"));
        var result = engine(collection, required).assess(target(), "test");
        assertThat(result.status()).isEqualTo(required ? AssessmentStatus.PARTIAL : AssessmentStatus.COMPLETE);
        assertThat(result.confidence()).isEqualTo(required ? AssessmentConfidence.MEDIUM : AssessmentConfidence.HIGH);
        assertThat(result.scoreAvailable()).isEqualTo(!required);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void abortedMetricsCollectionIsPartialButOnlyDowngradesProfilesRequiringMetrics(boolean required) {
        Target base = target();
        Target target = new Target(base.id(), base.displayName(), base.type(), base.environment(), true,
                base.keycloak(), base.infrastructure(),
                new ObservabilityTargetConfiguration("PROMETHEUS", null, "http://localhost:9090", null, null, "NAMESPACE"),
                Map.of());
        var keycloak = mock(KeycloakEvidenceCollector.class);
        var infra = mock(InfrastructureEvidenceCollector.class);
        var metrics = mock(MetricsEvidenceCollector.class);
        when(keycloak.source()).thenReturn("keycloak");
        when(infra.source()).thenReturn("infrastructure");
        when(metrics.source()).thenReturn("metrics");
        when(keycloak.collect(target)).thenReturn(List.of(marker("keycloak", true)));
        when(infra.collect(target)).thenReturn(List.of(marker("infrastructure", true)));
        when(metrics.collect(target)).thenReturn(List.of(marker("metrics", false),
                new Evidence("lab-a", "metrics", "performance", "metrics.http.requestRate", 1.0, Instant.EPOCH)));

        var collection = new AssessmentEvidenceService(keycloak, infra, metrics).collect(target);
        assertThat(collection.collectedSources()).contains("keycloak", "infrastructure", "metrics");
        assertThat(collection.partialSources()).containsExactly("metrics");
        assertThat(collection.failedSources()).isEmpty();
        var result = engine(collection, required).assess(target, "test");
        assertThat(result.status()).isEqualTo(required ? AssessmentStatus.PARTIAL : AssessmentStatus.COMPLETE);
        assertThat(result.evidenceCompleteness()).isEqualTo(required ? 99 : 100);
        assertThat(result.confidence()).isEqualTo(required ? AssessmentConfidence.MEDIUM : AssessmentConfidence.HIGH);
        assertThat(result.scoreAvailable()).isEqualTo(!required);
        assertThat(result.evidence()).anySatisfy(evidence -> {
            assertThat(evidence.key()).isEqualTo("metrics.http.requestRate");
            assertThat(evidence.value()).isEqualTo(1.0);
        });
    }

    @Test
    void optionalMetricsStillLeaveSelectedPerformanceRulesUnevaluated() {
        var collection = new AssessmentEvidenceService.EvidenceCollectionResult(
                List.of(marker("keycloak", true), marker("infrastructure", true), marker("metrics", false)),
                List.of("keycloak", "infrastructure", "metrics"), List.of(), List.of("metrics"));
        var result = engine(collection, false, 1).assess(target(), "test");
        assertThat(result.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(result.evidenceCompleteness()).isEqualTo(50);
        assertThat(result.rulesNotEvaluated()).isEqualTo(1);
        assertThat(result.scoreAvailable()).isFalse();
    }

    private static AssessmentEvidenceService service(Target target, String partialSource, Object value) {
        var keycloak = mock(KeycloakEvidenceCollector.class);
        var infra = mock(InfrastructureEvidenceCollector.class);
        when(keycloak.source()).thenReturn("keycloak");
        when(infra.source()).thenReturn("infrastructure");
        when(keycloak.collect(target)).thenReturn(List.of(marker("keycloak", "keycloak".equals(partialSource) ? value : true)));
        when(infra.collect(target)).thenReturn(List.of(marker("infrastructure", "infrastructure".equals(partialSource) ? value : true)));
        return new AssessmentEvidenceService(keycloak, infra, mock(MetricsEvidenceCollector.class));
    }

    private static AssessmentEngine engine(AssessmentEvidenceService.EvidenceCollectionResult collection) {
        return engine(collection, false);
    }

    private static AssessmentEngine engine(AssessmentEvidenceService.EvidenceCollectionResult collection, boolean metricsRequired) {
        return engine(collection, metricsRequired, 0);
    }

    private static AssessmentEngine engine(AssessmentEvidenceService.EvidenceCollectionResult collection,
            boolean metricsRequired, int rulesNotEvaluated) {
        var service = mock(AssessmentEvidenceService.class);
        when(service.collect(any())).thenReturn(collection);
        var profiles = mock(ProfileRegistry.class);
        var profile = new AssessmentProfile("test", "", List.of(), List.of(), List.of(),
                metricsRequired ? List.of("keycloak", "infrastructure", "metrics") : List.of("keycloak", "infrastructure"), List.of());
        when(profiles.require("test")).thenReturn(profile);
        var loader = mock(YamlRuleLoader.class);
        when(loader.loadForProfile(profile)).thenReturn(List.of());
        var rules = mock(RuleEngine.class);
        when(rules.evaluateDetailed(any(), any())).thenReturn(
                new RuleEvaluationResult(List.of(), 1, 0, 0, rulesNotEvaluated,
                        rulesNotEvaluated == 0 ? List.of() : List.of("metrics.http.p99Ms")));
        return new AssessmentEngine(profiles, mock(AssessmentProfileResolver.class), loader,
                rules, new AssessmentScoring(), TestAssessmentConfig.defaults(), service, mock(McpMetrics.class));
    }

    private static Evidence marker(String source, Object value) {
        return new Evidence("lab-a", source, "collection", source + ".collection.complete", value, Instant.EPOCH);
    }

    private static Target target() {
        return new Target(TargetId.of("lab-a"), "Lab A", TargetType.KEYCLOAK, TargetEnvironment.DEV, true,
                new KeycloakTargetConfiguration("http://localhost", "master", "c", "ref"),
                new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "cluster", "namespace", "ref"),
                null, Map.of());
    }
}
