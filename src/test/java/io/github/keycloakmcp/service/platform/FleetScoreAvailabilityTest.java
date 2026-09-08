package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.domain.platform.AssessmentRunSummary;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.persistence.entity.AssessmentRunEntity;
import io.github.keycloakmcp.persistence.mapper.AssessmentPersistenceMapper;
import io.github.keycloakmcp.persistence.mapper.PlatformPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.AssessmentRepository;
import io.github.keycloakmcp.persistence.repository.HealthCheckRepository;
import io.github.keycloakmcp.persistence.repository.SnapshotRepository;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetRegistry;
import io.github.keycloakmcp.target.TargetType;

class FleetScoreAvailabilityTest {
    @Test
    void fleetOnlyExposesScoresWithTrustedAvailability() {
        Target target = new Target(TargetId.of("target-a"), "A", TargetType.KEYCLOAK, TargetEnvironment.TEST,
                true, new KeycloakTargetConfiguration("http://localhost", "master", "client", "ref"),
                null, null, Map.of());
        TargetRegistry targets = mock(TargetRegistry.class);
        TargetAuthorizationService authorization = mock(TargetAuthorizationService.class);
        AssessmentRepository assessments = mock(AssessmentRepository.class);
        AssessmentPersistenceMapper mapper = mock(AssessmentPersistenceMapper.class);
        when(targets.list()).thenReturn(List.of(target));
        when(authorization.isAllowed(target, TargetPermission.READ)).thenReturn(true);
        AssessmentRunEntity row = new AssessmentRunEntity();
        when(assessments.findLatest("target-a")).thenReturn(Optional.of(row));
        FleetService fleet = new FleetService(targets, authorization, assessments,
                mock(HealthCheckRepository.class), mock(SnapshotRepository.class), mapper,
                mock(PlatformPersistenceMapper.class));
        Instant now = Instant.now();
        when(mapper.toSummary(row)).thenReturn(new AssessmentRunSummary("legacy", "target-a", "profile", 100,
                "COMPLETE", TriggerType.API, now, now, now, 100, "HIGH", Map.of(), Map.of()));
        assertThat(fleet.fleet().getFirst().latestAssessmentScore()).isNull();
        assertThat(fleet.fleet().getFirst().scoreAvailable()).isNull();
        when(mapper.toSummary(row)).thenReturn(new AssessmentRunSummary("current", "target-a", "profile", 100,
                "COMPLETE", TriggerType.API, now, now, now, 100, "HIGH", Map.of(), Map.of(), true, "known"));
        assertThat(fleet.fleet().getFirst().latestAssessmentScore()).isEqualTo(100);
        assertThat(fleet.fleet().getFirst().scoreAvailable()).isTrue();
    }
}
