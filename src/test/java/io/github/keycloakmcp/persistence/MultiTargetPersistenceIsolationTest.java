package io.github.keycloakmcp.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.persistence.entity.AssessmentRunEntity;
import io.github.keycloakmcp.persistence.entity.TargetEntity;
import io.github.keycloakmcp.persistence.repository.AssessmentRepository;
import io.github.keycloakmcp.persistence.repository.TargetRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class MultiTargetPersistenceIsolationTest {

    @Inject
    TargetRepository targetRepository;

    @Inject
    AssessmentRepository assessmentRepository;

    @Test
    @Transactional
    void assessmentsAreIsolatedByTargetId() {
        String a = ensureTarget("iso-a-" + UUID.randomUUID());
        String b = ensureTarget("iso-b-" + UUID.randomUUID());
        persistRun(a, 10);
        persistRun(b, 90);

        assertThat(assessmentRepository.listByTarget(a, 0, 20).items())
                .allMatch(r -> a.equals(r.targetId));
        assertThat(assessmentRepository.listByTarget(b, 0, 20).items())
                .allMatch(r -> b.equals(r.targetId));
        assertThat(assessmentRepository.findLatest(a).orElseThrow().score).isEqualTo(10);
        assertThat(assessmentRepository.findLatest(b).orElseThrow().score).isEqualTo(90);
    }

    private void persistRun(String targetId, int score) {
        AssessmentRunEntity run = new AssessmentRunEntity();
        run.id = UUID.randomUUID().toString();
        run.targetId = targetId;
        run.profile = "keycloak-production";
        run.score = score;
        run.status = "COMPLETED";
        run.triggerType = "API";
        run.summary = Map.of("score", score);
        run.startedAt = Instant.now();
        run.completedAt = Instant.now();
        run.createdAt = Instant.now();
        assessmentRepository.persist(run);
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
