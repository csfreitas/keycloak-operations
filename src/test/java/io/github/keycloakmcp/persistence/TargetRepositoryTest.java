package io.github.keycloakmcp.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.persistence.entity.TargetEntity;
import io.github.keycloakmcp.persistence.repository.TargetRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class TargetRepositoryTest {

    @Inject
    TargetRepository targetRepository;

    @Test
    @Transactional
    void persistsAndFindsTargetWithoutSecrets() {
        String id = "repo-target-" + UUID.randomUUID();
        TargetEntity entity = new TargetEntity();
        entity.id = id;
        entity.displayName = "Repo Target";
        entity.productType = "KEYCLOAK";
        entity.environment = "TEST";
        entity.enabled = true;
        entity.keycloakUrl = "http://localhost:8080";
        entity.keycloakAuthRealm = "master";
        entity.keycloakClientId = "keycloak-mcp";
        entity.keycloakCredentialRef = "lab-a";
        entity.createdAt = Instant.now();
        entity.updatedAt = Instant.now();
        entity.tags = Map.of("suite", "repo");
        targetRepository.persist(entity);

        TargetEntity found = targetRepository.findOptionalById(id).orElseThrow();
        assertThat(found.displayName).isEqualTo("Repo Target");
        assertThat(found.keycloakCredentialRef).isEqualTo("lab-a");
        assertThat(found.keycloakUrl).doesNotContain("secret");
        String json = found.keycloakCredentialRef + found.displayName + String.valueOf(found.observability);
        assertThat(json).doesNotContain("client-secret");
        assertThat(json.toLowerCase()).doesNotContain("clientsecret");
    }
}
