package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.assessment.engine.*;
import io.github.keycloakmcp.domain.platform.OperationalEvent;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.persistence.entity.AssessmentRunEntity;
import io.github.keycloakmcp.persistence.mapper.AssessmentPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.AssessmentRepository;
import io.github.keycloakmcp.persistence.repository.FindingRepository;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.target.*;

class AssessmentHistoryServiceEventTest {
    @Test
    void partialLegacyHundredScoreIsNotAdvertisedAsHealthy() {
        OperationalEvent event = run(AssessmentStatus.PARTIAL, 99, 1);
        assertThat(event.message()).isEqualTo("Assessment inconclusive (PARTIAL)").doesNotContain("100");
        assertThat(event.type()).isEqualTo("assessment_completed");
        assertThat(event.targetId()).isEqualTo("test");
        assertThat(event.relatedId()).isNotBlank();
    }

    @Test
    void completeAssessedScoreRetainsExistingEventMessage() {
        assertThat(run(AssessmentStatus.COMPLETE, 100, 0).message()).isEqualTo("Assessment score 100");
    }

    private OperationalEvent run(AssessmentStatus status, int completeness, int unevaluated) {
        var engine = mock(AssessmentEngine.class);
        var resolver = mock(TargetResolver.class);
        var authorization = mock(TargetAuthorizationService.class);
        var runs = mock(AssessmentRepository.class);
        var findings = mock(FindingRepository.class);
        var mapper = mock(AssessmentPersistenceMapper.class);
        var events = mock(OperationalEventBus.class);
        var target = new Target(TargetId.of("test"), "test", TargetType.KEYCLOAK, TargetEnvironment.DEV,
                true, new KeycloakTargetConfiguration("http://localhost", "master", "client", "ref"),
                null, null, Map.of());
        when(resolver.require("test")).thenReturn(target);
        var result = new AssessmentResult(null, "test", "production", null, status, 100, Map.of(), completeness,
                null, 1, 0, 0, unevaluated, List.of(), List.of(), List.of(), Instant.now(), Instant.now());
        when(engine.assess(target, "production")).thenReturn(result);
        when(mapper.toRunEntity(eq(result), anyString(), eq(TriggerType.API))).thenReturn(new AssessmentRunEntity());
        when(mapper.toFindingEntities(eq(result), anyString())).thenReturn(List.of());
        var service = new AssessmentHistoryService(engine, resolver, authorization, runs, findings, mapper, events,
                new SensitiveDataFilter(new ObjectMapper().findAndRegisterModules()));
        service.runAndPersist("test", "production", TriggerType.API);
        var captured = ArgumentCaptor.forClass(OperationalEvent.class);
        verify(events).publish(captured.capture());
        verify(authorization).assertAllowed(target, TargetPermission.ASSESS);
        return captured.getValue();
    }
}
