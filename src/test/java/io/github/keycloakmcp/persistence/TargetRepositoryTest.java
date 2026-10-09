package io.github.keycloakmcp.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.persistence.entity.TargetEntity;
import io.github.keycloakmcp.persistence.repository.TargetRepository;
import io.github.keycloakmcp.persistence.mapper.TargetPersistenceMapper;
import io.github.keycloakmcp.target.*;
import jakarta.persistence.EntityManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class TargetRepositoryTest {

    @Inject
    TargetRepository targetRepository;

    @Inject
    TargetPersistenceMapper mapper;

    @Inject
    EntityManager entityManager;

    @Test
    @Transactional
    void installationBindingSurvivesDatabaseRoundTripAndCanBeCleared() {
        String id = "binding-" + UUID.randomUUID();
        var binding = new KubernetesInstallationBinding("apps/v1", "Deployment", "rhbk", UUID.randomUUID().toString());
        var target = new Target(TargetId.of(id), "Bound", TargetType.RHBK, TargetEnvironment.TEST, true,
                new KeycloakTargetConfiguration("http://localhost:8080", "master", "keycloak-mcp", "lab-a"),
                new InfrastructureTargetConfiguration(InfrastructureType.OPENSHIFT, "lab-cluster", "iam", "infra-ref", binding),
                null, Map.of());
        targetRepository.persist(mapper.toEntity(target));
        entityManager.flush();
        entityManager.clear();
        var persisted = targetRepository.findOptionalById(id).orElseThrow();
        assertThat(mapper.toDomain(persisted).infrastructure().installation()).isEqualTo(binding);
        var unbound = new Target(target.id(), target.displayName(), target.type(), target.environment(), true,
                target.keycloak(), new InfrastructureTargetConfiguration(InfrastructureType.OPENSHIFT, "lab-cluster", "iam", "infra-ref"),
                null, Map.of());
        mapper.updateEntity(unbound, persisted);
        entityManager.flush();
        entityManager.clear();
        persisted = targetRepository.findOptionalById(id).orElseThrow();
        assertThat(mapper.toDomain(persisted).infrastructure().installation()).isNull();
        assertThat(persisted.installationApiVersion).isNull();
        assertThat(persisted.installationKind).isNull();
        assertThat(persisted.installationName).isNull();
    }

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
