package io.github.keycloakmcp.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.persistence.entity.AssessmentFindingEntity;
import io.github.keycloakmcp.persistence.entity.AssessmentRunEntity;
import io.github.keycloakmcp.persistence.entity.TargetEntity;
import io.github.keycloakmcp.persistence.repository.AssessmentRepository;
import io.github.keycloakmcp.persistence.repository.FindingRepository;
import io.github.keycloakmcp.persistence.repository.TargetRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class AssessmentRepositoryTest {

    @Inject
    TargetRepository targetRepository;

    @Inject
    AssessmentRepository assessmentRepository;

    @Inject
    FindingRepository findingRepository;

    @Test
    @Transactional
    void persistsRunAndListsByTarget() {
        String targetId = ensureTarget("assess-target-" + UUID.randomUUID());
        String runId = UUID.randomUUID().toString();

        AssessmentRunEntity run = new AssessmentRunEntity();
        run.id = runId;
        run.targetId = targetId;
        run.profile = "keycloak-production";
        run.score = 80;
        run.status = "COMPLETED";
        run.triggerType = "API";
        run.summary = Map.of("findingCount", 1);
        run.startedAt = Instant.now();
        run.completedAt = Instant.now();
        run.createdAt = Instant.now();
        assessmentRepository.persist(run);

        AssessmentFindingEntity finding = new AssessmentFindingEntity();
        finding.id = UUID.randomUUID().toString();
        finding.assessmentId = runId;
        finding.targetId = targetId;
        finding.findingKey = "KC-TEST-001";
        finding.title = "Test finding";
        finding.severity = "HIGH";
        finding.engineStatus = "FAIL";
        finding.lifecycleStatus = "OPEN";
        finding.createdAt = Instant.now();
        findingRepository.persist(finding);

        assertThat(assessmentRepository.listByTarget(targetId, 0, 10).items()).hasSize(1);
        assertThat(assessmentRepository.findLatest(targetId)).isPresent();
        assertThat(findingRepository.listByAssessment(runId)).hasSize(1);
    }

    private String ensureTarget(String id) {
        TargetEntity entity = new TargetEntity();
        entity.id = id;
        entity.displayName = id;
        entity.productType = "KEYCLOAK";
        entity.environment = "TEST";
        entity.enabled = true;
        entity.keycloakUrl = "http://localhost:8080";
        entity.keycloakAuthRealm = "master";
        entity.keycloakClientId = "keycloak-mcp";
        entity.keycloakCredentialRef = "lab-a";
        entity.createdAt = Instant.now();
        entity.updatedAt = Instant.now();
        targetRepository.persist(entity);
        return id;
    }
}
