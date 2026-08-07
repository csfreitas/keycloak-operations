package io.github.keycloakmcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.ClientRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.client.ClientDetails;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetResolver;
import io.github.keycloakmcp.target.TargetType;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    private static final String TARGET_ID = "local-dev";

    @Mock
    private StableAdminApiAdapter adminApi;

    @Mock
    private TargetResolver targetResolver;

    @Mock
    private TargetAuthorizationService targetAuthorization;

    @Mock
    private AuditService auditService;

    private ClientService clientService;
    private ObjectMapper objectMapper;
    private Target target;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        clientService = new ClientService(
                adminApi, targetResolver, targetAuthorization, new SensitiveDataFilter(objectMapper), auditService);
        target = sampleTarget();
        when(targetResolver.require(TARGET_ID)).thenReturn(target);
    }

    @Test
    void getClientDoesNotExposeSecretsInClientDetails() {
        ClientRepresentation representation = new ClientRepresentation();
        representation.setId("uuid-1");
        representation.setClientId("backend-api");
        representation.setName("Backend API");
        representation.setEnabled(true);
        representation.setPublicClient(false);
        representation.setServiceAccountsEnabled(true);
        representation.setStandardFlowEnabled(false);
        representation.setDirectAccessGrantsEnabled(false);
        representation.setProtocol("openid-connect");
        representation.setRootUrl("http://localhost:8082");
        representation.setBaseUrl("/");
        representation.setRedirectUris(List.of("http://localhost:8082/*"));
        representation.setWebOrigins(List.of("+"));
        representation.setBearerOnly(false);
        representation.setFullScopeAllowed(true);
        representation.setDescription("Confidential service client");
        representation.setSecret("backend-api-secret");

        when(adminApi.findClientByClientId(target, "mcp-demo", "backend-api")).thenReturn(representation);

        ClientDetails details = clientService.getClient(TARGET_ID, "mcp-demo", "backend-api");

        assertThat(details.clientId()).isEqualTo("backend-api");
        assertThat(details.name()).isEqualTo("Backend API");
        assertThat(details.publicClient()).isFalse();
        assertThat(details.serviceAccountsEnabled()).isTrue();
        assertThat(details.protocol()).isEqualTo("openid-connect");

        String json = objectMapper.valueToTree(details).toString();
        assertThat(json).doesNotContain("backend-api-secret");
        assertThat(json.toLowerCase()).doesNotContain("\"secret\"");
        assertThat(json.toLowerCase()).doesNotContain("clientsecret");
    }

    private static Target sampleTarget() {
        return new Target(
                TargetId.of(TARGET_ID),
                "Local Dev",
                TargetType.KEYCLOAK,
                TargetEnvironment.DEV,
                true,
                new KeycloakTargetConfiguration("http://localhost:8080", "master", "keycloak-mcp", "local-dev"),
                null,
                null,
                Map.of());
    }
}
