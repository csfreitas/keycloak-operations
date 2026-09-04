package io.github.keycloakmcp.it;

import static io.github.keycloakmcp.it.DisposableKeycloakGuard.TARGET_ID;
import static io.github.keycloakmcp.it.DisposableKeycloakGuard.requireDisposableLoopback;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.representations.idm.ClientRepresentation;

import io.github.keycloakmcp.adapter.keycloak.KeycloakClientFactory;
import io.github.keycloakmcp.domain.change.ChangeRecord;
import io.github.keycloakmcp.domain.change.ChangeStatus;
import io.github.keycloakmcp.domain.change.ClientUrlChangeRequest;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.persistence.entity.ChangeRecordEntity;
import io.github.keycloakmcp.persistence.repository.ChangeRepository;
import io.github.keycloakmcp.service.change.ChangeManagementService;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetResolver;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

/**
 * Real opt-in controlled-write coverage using a dedicated client fixture in the
 * imported realm of the local disposable Community Keycloak stack.
 */
@QuarkusTest
@TestProfile(DisposableKeycloakIntegrationTestProfile.class)
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "RUN_KEYCLOAK_IT", matches = "true")
class ControlledClientChangeIT {

    private static final List<String> ORIGINAL_REDIRECTS = List.of("https://original.example/callback");
    private static final List<String> ORIGINAL_ORIGINS = List.of("https://original.example");
    private static final List<String> UPDATED_REDIRECTS = List.of("https://updated.example/callback");
    private static final List<String> UPDATED_ORIGINS = List.of("https://updated.example");
    private static final String DISPOSABLE_REALM = "mcp-demo";

    @Inject
    TargetResolver targetResolver;

    @Inject
    KeycloakClientFactory clientFactory;

    @Inject
    ChangeManagementService changeManagementService;

    @Inject
    ChangeRepository changeRepository;

    private Target target;
    private Keycloak keycloak;
    private String realm;
    private String clientId;
    private String clientUuid;

    @BeforeEach
    void createDedicatedFixture() {
        target = targetResolver.require(TARGET_ID);
        requireDisposableLoopback(target);
        keycloak = clientFactory.getClient(target);

        String suffix = UUID.randomUUID().toString();
        realm = DISPOSABLE_REALM;
        clientId = "kcops-client-it-" + suffix;
        assertThat(keycloak.realm(realm).toRepresentation().getRealm()).isEqualTo(DISPOSABLE_REALM);

        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(clientId);
        client.setName("Disposable controlled-write fixture");
        client.setDescription("Created only for local integration validation");
        client.setEnabled(true);
        client.setPublicClient(true);
        client.setProtocol("openid-connect");
        client.setStandardFlowEnabled(true);
        client.setRedirectUris(new ArrayList<>(ORIGINAL_REDIRECTS));
        client.setWebOrigins(new ArrayList<>(ORIGINAL_ORIGINS));
        try (Response response = clients().create(client)) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
        }
        clientUuid = clients().findByClientId(clientId).getFirst().getId();
        assertThat(clientUuid).isNotBlank();
    }

    @AfterEach
    void removeDedicatedFixture() {
        if (keycloak == null || realm == null || clientId == null) {
            return;
        }
        List<ClientRepresentation> matches = clients().findByClientId(clientId);
        if (!matches.isEmpty()) {
            clients().get(matches.getFirst().getId()).remove();
        }
        assertThat(clients().findByClientId(clientId)).isEmpty();
    }

    @Test
    void planApproveApplyReadBackAndRestoreClientUrls() {
        ChangeRecord planned = plan(UPDATED_REDIRECTS, UPDATED_ORIGINS, "apply");
        assertTargetIsolation(planned);
        assertNoCredentialLeakage(planned);

        ChangeRecord applied = approveWhenRequiredAndApply(planned);
        assertThat(applied.status()).isEqualTo(ChangeStatus.VERIFIED);
        assertThat(applied.verificationStatus()).isEqualTo("VERIFIED");
        assertClientUrls(UPDATED_REDIRECTS, UPDATED_ORIGINS);

        ChangeRecord restorePlan = plan(ORIGINAL_REDIRECTS, ORIGINAL_ORIGINS, "restore");
        assertTargetIsolation(restorePlan);
        assertNoCredentialLeakage(restorePlan);
        ChangeRecord restored = approveWhenRequiredAndApply(restorePlan);

        assertThat(restored.status()).isEqualTo(ChangeStatus.VERIFIED);
        assertThat(restored.verificationStatus()).isEqualTo("VERIFIED");
        assertClientUrls(ORIGINAL_REDIRECTS, ORIGINAL_ORIGINS);
        assertThat(readClient().getDescription()).isEqualTo("Created only for local integration validation");
    }

    @Test
    void stalePlanIsRejectedWithoutOverwritingTheFixture() {
        ChangeRecord planned = plan(UPDATED_REDIRECTS, null, "stale");
        if (planned.requiresApproval()) {
            changeManagementService.approve(planned.changeId(), "disposable-it-approver");
        }

        ClientRepresentation externallyChanged = readClient();
        List<String> driftedRedirects = List.of("https://drift.example/callback");
        externallyChanged.setRedirectUris(new ArrayList<>(driftedRedirects));
        clients().get(clientUuid).update(externallyChanged);

        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "disposable-it-applier"))
                .isInstanceOf(McpException.class)
                .satisfies(error -> assertThat(((McpException) error).getCode())
                        .isEqualTo(ErrorCode.CHANGE_CONFLICT));
        assertThat(changeManagementService.getChange(planned.changeId()).status()).isEqualTo(ChangeStatus.FAILED);
        assertClientUrls(driftedRedirects, ORIGINAL_ORIGINS);

        externallyChanged = readClient();
        externallyChanged.setRedirectUris(new ArrayList<>(ORIGINAL_REDIRECTS));
        clients().get(clientUuid).update(externallyChanged);
        assertClientUrls(ORIGINAL_REDIRECTS, ORIGINAL_ORIGINS);
    }

    private ChangeRecord plan(List<String> redirects, List<String> origins, String operation) {
        return changeManagementService.planClientUrlUpdate(new ClientUrlChangeRequest(
                TARGET_ID,
                realm,
                clientId,
                redirects,
                origins,
                "disposable-it-planner",
                "disposable-it-" + operation + "-" + UUID.randomUUID()));
    }

    private ChangeRecord approveWhenRequiredAndApply(ChangeRecord planned) {
        if (planned.requiresApproval()) {
            ChangeRecord approved = changeManagementService.approve(
                    planned.changeId(), "disposable-it-approver");
            assertThat(approved.approvalFingerprint()).isEqualTo(approved.planFingerprint());
        }
        return changeManagementService.apply(planned.changeId(), "disposable-it-applier");
    }

    private void assertTargetIsolation(ChangeRecord record) {
        assertThat(record.targetId()).isEqualTo(TARGET_ID);
        assertThat(record.realm()).isEqualTo(realm);
        assertThat(record.resourceId()).isEqualTo(clientId);
        assertThat(changeManagementService.listChanges(Optional.of(TARGET_ID), Optional.empty(), 0, 100).items())
                .filteredOn(item -> item.changeId().equals(record.changeId()))
                .allMatch(item -> TARGET_ID.equals(item.targetId()) && realm.equals(item.realm()))
                .hasSize(1);
    }

    private void assertNoCredentialLeakage(ChangeRecord record) {
        String configuredSecret = System.getenv("KEYCLOAK_CLIENT_SECRET");
        if (configuredSecret == null || configuredSecret.isBlank()) {
            return;
        }
        ChangeRecordEntity persisted = changeRepository.findById(record.changeId());
        String persistedPlan = String.valueOf(persisted.desiredState)
                + persisted.diffJson
                + persisted.operationsJson
                + persisted.planFingerprint
                + persisted.baselineFingerprint;
        assertThat(record.toString().contains(configuredSecret))
                .as("API/domain response must not contain credential material")
                .isFalse();
        assertThat(persistedPlan.contains(configuredSecret))
                .as("persistence, diff, operations and fingerprints must not contain credential material")
                .isFalse();
    }

    private void assertClientUrls(List<String> redirects, List<String> origins) {
        ClientRepresentation actual = readClient();
        assertThat(actual.getRedirectUris()).containsExactlyElementsOf(redirects);
        assertThat(actual.getWebOrigins()).containsExactlyElementsOf(origins);
    }

    private ClientRepresentation readClient() {
        return clients().get(clientUuid).toRepresentation();
    }

    private ClientsResource clients() {
        return keycloak.realm(realm).clients();
    }
}
