package io.github.keycloakmcp.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.persistence.entity.HealthCheckResultEntity;
import io.github.keycloakmcp.persistence.entity.HealthCheckRunEntity;
import io.github.keycloakmcp.persistence.entity.TargetEntity;
import io.github.keycloakmcp.persistence.repository.HealthCheckRepository;
import io.github.keycloakmcp.persistence.repository.TargetRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class HealthCheckRepositoryTest {

    @Inject
    TargetRepository targetRepository;

    @Inject
    HealthCheckRepository healthCheckRepository;

    @Test
    @Transactional
    void persistsRunWithResults() {
        String targetId = ensureTarget("health-target-" + UUID.randomUUID());
        String runId = UUID.randomUUID().toString();

        HealthCheckRunEntity run = new HealthCheckRunEntity();
        run.id = runId;
        run.targetId = targetId;
        run.overallStatus = "HEALTHY";
        run.triggerType = "API";
        run.startedAt = Instant.now();
        run.completedAt = Instant.now();
        run.createdAt = Instant.now();

        HealthCheckResultEntity result = new HealthCheckResultEntity();
        result.id = UUID.randomUUID().toString();
        result.healthCheckId = runId;
        result.targetId = targetId;
        result.checkName = "keycloak.serverInfo";
        result.status = "HEALTHY";
        result.details = Map.of("ok", true);
        result.createdAt = Instant.now();

        healthCheckRepository.persistRunWithResults(run, List.of(result));

        assertThat(healthCheckRepository.findLatest(targetId)).isPresent();
        assertThat(healthCheckRepository.listResults(runId)).hasSize(1);
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
