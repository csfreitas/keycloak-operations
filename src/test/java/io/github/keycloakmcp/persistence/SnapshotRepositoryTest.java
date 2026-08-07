package io.github.keycloakmcp.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.persistence.entity.EnvironmentSnapshotEntity;
import io.github.keycloakmcp.persistence.entity.InventorySnapshotEntity;
import io.github.keycloakmcp.persistence.entity.TargetEntity;
import io.github.keycloakmcp.persistence.repository.SnapshotRepository;
import io.github.keycloakmcp.persistence.repository.TargetRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class SnapshotRepositoryTest {

    @Inject
    TargetRepository targetRepository;

    @Inject
    SnapshotRepository snapshotRepository;

    @Test
    @Transactional
    void persistsEnvironmentAndInventory() {
        String targetId = ensureTarget("snap-target-" + UUID.randomUUID());
        String envId = UUID.randomUUID().toString();

        EnvironmentSnapshotEntity env = new EnvironmentSnapshotEntity();
        env.id = envId;
        env.targetId = targetId;
        env.snapshotHash = "abc123";
        env.summary = Map.of("productType", "KEYCLOAK");
        env.createdAt = Instant.now();

        InventorySnapshotEntity inv = new InventorySnapshotEntity();
        inv.id = UUID.randomUUID().toString();
        inv.targetId = targetId;
        inv.environmentSnapshotId = envId;
        inv.inventoryType = "basic";
        inv.summary = Map.of("note", "test");
        inv.createdAt = Instant.now();

        snapshotRepository.persistWithInventory(env, inv);

        assertThat(snapshotRepository.findLatest(targetId)).isPresent();
        assertThat(snapshotRepository.listByTarget(targetId, 0, 10).items()).hasSize(1);
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
