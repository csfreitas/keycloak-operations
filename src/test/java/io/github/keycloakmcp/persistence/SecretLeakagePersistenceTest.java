package io.github.keycloakmcp.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.persistence.entity.TargetEntity;
import io.github.keycloakmcp.persistence.mapper.TargetPersistenceMapper;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class SecretLeakagePersistenceTest {

    @Inject
    TargetPersistenceMapper mapper;

    @Inject
    SensitiveDataFilter sensitiveDataFilter;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void entityAndJsonNeverContainClientSecret() throws Exception {
        Target target = new Target(
                TargetId.of("secret-check"),
                "Secret Check",
                TargetType.KEYCLOAK,
                TargetEnvironment.TEST,
                true,
                new KeycloakTargetConfiguration(
                        "http://localhost:8080", "master", "keycloak-mcp", "lab-a"),
                null,
                null,
                Map.of());

        TargetEntity entity = mapper.toEntity(target);
        assertThat(entity.keycloakCredentialRef).isEqualTo("lab-a");

        Map<String, Object> asMap = objectMapper.convertValue(entity, Map.class);
        asMap.put("clientSecret", "super-secret-value");
        Map<String, Object> redacted = sensitiveDataFilter.redact(new HashMap<>(asMap));

        String json = objectMapper.writeValueAsString(redacted);
        assertThat(json).doesNotContain("super-secret-value");
        assertThat(json.toLowerCase()).contains("[redacted]");
        assertThat(objectMapper.writeValueAsString(entity)).doesNotContain("super-secret");
    }
}
