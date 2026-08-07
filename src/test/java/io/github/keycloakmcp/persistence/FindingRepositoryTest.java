package io.github.keycloakmcp.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
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
class FindingRepositoryTest {

    @Inject
    TargetRepository targetRepository;

    @Inject
    AssessmentRepository assessmentRepository;

    @Inject
    FindingRepository findingRepository;

    @Test
    @Transactional
    void filtersByLifecycleAndSeverity() {
        String targetId = ensureTarget("finding-target-" + UUID.randomUUID());
        String runId = UUID.randomUUID().toString();
        AssessmentRunEntity run = new AssessmentRunEntity();
        run.id = runId;
        run.targetId = targetId;
        run.profile = "keycloak-production";
        run.score = 50;
        run.status = "COMPLETED";
        run.triggerType = "API";
        run.startedAt = Instant.now();
        run.completedAt = Instant.now();
        run.createdAt = Instant.now();
        assessmentRepository.persist(run);

        persistFinding(runId, targetId, "OPEN", "HIGH");
        persistFinding(runId, targetId, "RESOLVED", "LOW");

        assertThat(findingRepository.listByTarget(targetId, Optional.of("OPEN"), Optional.empty(), 0, 20).total())
                .isEqualTo(1);
        assertThat(findingRepository.listByTarget(targetId, Optional.empty(), Optional.of("HIGH"), 0, 20).total())
                .isEqualTo(1);
    }

    private void persistFinding(String runId, String targetId, String lifecycle, String severity) {
        AssessmentFindingEntity finding = new AssessmentFindingEntity();
        finding.id = UUID.randomUUID().toString();
        finding.assessmentId = runId;
        finding.targetId = targetId;
        finding.findingKey = "F-" + finding.id.substring(0, 8);
        finding.title = "Finding";
        finding.severity = severity;
        finding.engineStatus = "FAIL";
        finding.lifecycleStatus = lifecycle;
        finding.createdAt = Instant.now();
        findingRepository.persist(finding);
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
