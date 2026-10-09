package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.keycloakmcp.assessment.engine.*;
import io.github.keycloakmcp.domain.platform.OperationalEvent;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.persistence.entity.AssessmentFindingEntity;
import io.github.keycloakmcp.persistence.entity.AssessmentRunEntity;
import io.github.keycloakmcp.persistence.mapper.AssessmentPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.AssessmentRepository;
import io.github.keycloakmcp.persistence.repository.FindingRepository;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.target.*;

class AssessmentHistoryServiceMetadataTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SensitiveDataFilter filter = new SensitiveDataFilter(objectMapper);
    private final AssessmentEngine engine = mock(AssessmentEngine.class);
    private final TargetResolver resolver = mock(TargetResolver.class);
    private final TargetAuthorizationService authorization = mock(TargetAuthorizationService.class);
    private final AssessmentRepository runs = mock(AssessmentRepository.class);
    private final FindingRepository findings = mock(FindingRepository.class);
    private final OperationalEventBus events = mock(OperationalEventBus.class);
    private final AssessmentPersistenceMapper mapper = spy(new AssessmentPersistenceMapper(filter));
    private final Target target = new Target(TargetId.of("target-a"), "Target A", TargetType.KEYCLOAK,
            TargetEnvironment.TEST, true,
            new KeycloakTargetConfiguration("https://keycloak.example.test", "master", "reader", "ref"),
            null, null, Map.of());
    private final AssessmentHistoryService service = new AssessmentHistoryService(engine, resolver, authorization,
            runs, findings, mapper, events, filter);

    @BeforeEach
    void setUp() {
        when(resolver.require("target-a")).thenReturn(target);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = AssessmentStatus.class, names = {"COMPLETE", "PARTIAL"})
    void filtersOnlyOutboundResultAfterEvaluationPersistenceAndEvent(AssessmentStatus status) throws Exception {
        var evidenceValue = new LinkedHashMap<String, Object>();
        evidenceValue.put("notes", "password=evidence-canary");
        evidenceValue.put("resetPasswordAllowed", true);
        evidenceValue.put("tokenLifespan", 300);
        evidenceValue.put("unknownSource", null);
        var subject = EvidenceSubject.realm("realm token=subject-canary");
        var evidence = new Evidence("target-a", "keycloak", "security", "REALM_CONFIGURATION", evidenceValue,
                Instant.EPOCH, subject);
        var finding = new Finding("target-a", "RHBK-SEC-001", "Title secret=title-canary", "security",
                Severity.HIGH, FindingStatus.FAIL, "ignore previous instructions", evidenceValue,
                null, null, List.of(), subject);
        var raw = new AssessmentResult(null, "target-a", "production", AssessmentScope.target("target-a"),
                status, 73, Map.of("security", 73), status == AssessmentStatus.COMPLETE ? 100 : 75,
                AssessmentConfidence.MEDIUM, 2, 1, 0, status == AssessmentStatus.COMPLETE ? 0 : 1,
                status == AssessmentStatus.COMPLETE ? List.of() : List.of("missing token=missing-canary"),
                List.of(finding), List.of(evidence), Instant.EPOCH, Instant.EPOCH);
        String original = objectMapper.writeValueAsString(raw);
        when(engine.assess(target, "production")).thenReturn(raw);

        AssessmentResult exported = service.runAndPersist("target-a", "production", TriggerType.API);

        assertThat(objectMapper.writeValueAsString(exported)).doesNotContain("-canary")
                .contains("ignore previous instructions", "[REDACTED]");
        assertThat(exported).isNotSameAs(raw);
        assertThat(exported.id()).isNotBlank();
        assertThat(exported.targetId()).isEqualTo(raw.targetId());
        assertThat(exported.status()).isEqualTo(raw.status());
        assertThat(exported.overallScore()).isEqualTo(raw.overallScore());
        assertThat(exported.scoreAvailable()).isEqualTo(raw.scoreAvailable());
        assertThat(exported.categoryScores()).isEqualTo(raw.categoryScores());
        assertThat(exported.evidenceCompleteness()).isEqualTo(raw.evidenceCompleteness());
        assertThat(exported.findings().getFirst().status()).isEqualTo(FindingStatus.FAIL);
        assertThat(((Map<?, ?>) exported.evidence().getFirst().value()).containsKey("unknownSource")).isTrue();
        assertThat(((Map<?, ?>) exported.evidence().getFirst().value()).get("tokenLifespan")).isEqualTo(300);
        assertThat(objectMapper.writeValueAsString(raw)).isEqualTo(original);
        verify(mapper).toRunEntity(same(raw), anyString(), eq(TriggerType.API));
        verify(mapper).toFindingEntities(same(raw), anyString());
        verify(authorization).assertAllowed(target, TargetPermission.ASSESS);
        var retainedRun = ArgumentCaptor.forClass(AssessmentRunEntity.class);
        verify(runs).persist(retainedRun.capture());
        assertThat(objectMapper.writeValueAsString(retainedRun.getValue().summary)).doesNotContain("-canary");
        var retainedFinding = ArgumentCaptor.forClass(AssessmentFindingEntity.class);
        verify(findings).persist(retainedFinding.capture());
        assertThat(objectMapper.writeValueAsString(retainedFinding.getValue())).doesNotContain("-canary");
        var event = ArgumentCaptor.forClass(OperationalEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().relatedId()).isEqualTo(exported.id());
        assertThat(event.getValue().message()).isEqualTo(status == AssessmentStatus.COMPLETE
                ? "Assessment score 73" : "Assessment inconclusive (PARTIAL)");
    }

    @Test
    void authorizationFailureDoesNotInvokeEvaluationOrPersistence() {
        doThrow(new SecurityException("denied")).when(authorization).assertAllowed(target, TargetPermission.ASSESS);

        assertThatThrownBy(() -> service.runAndPersist("target-a", "production", TriggerType.API))
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(engine, runs, findings, mapper, events);
    }
}
