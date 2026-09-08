package io.github.keycloakmcp.service.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;

import io.github.keycloakmcp.domain.change.ChangeOperationType;
import io.github.keycloakmcp.domain.change.ClientCreateChangeRequest;
import io.github.keycloakmcp.domain.change.ClientEnabledChangeRequest;
import io.github.keycloakmcp.domain.error.McpException;

class ClientLifecycleChangeSupportTest {

    private final ClientLifecycleChangeSupport support = new ClientLifecycleChangeSupport();

    @Test
    void createsConservativeSecretFreeDesiredState() {
        var planned = support.planCreate(createRequest("new-client", null, null, null));

        assertThat(planned.baselineState()).containsEntry("exists", false);
        assertThat(planned.desiredState())
                .containsEntry("enabled", false)
                .containsEntry("publicClient", true)
                .containsEntry("standardFlowEnabled", true)
                .containsEntry("directAccessGrantsEnabled", false)
                .containsEntry("serviceAccountsEnabled", false)
                .doesNotContainKeys("secret", "clientSecret");
        assertThat(planned.operations()).allMatch(op -> op.operationType() == ChangeOperationType.CREATE);
        assertThat(support.toCreateRepresentation(planned.desiredState()).getSecret()).isNull();
    }

    @Test
    void rejectsUnsafeClientIdsAndPublicServiceAccounts() {
        assertThatThrownBy(() -> support.planCreate(createRequest("bad/client", null, null, null)))
                .isInstanceOf(McpException.class);
        assertThatThrownBy(() -> support.planCreate(createRequest("service", true, true, false)))
                .isInstanceOf(McpException.class)
                .hasMessageContaining("confidential");
    }

    @Test
    void acceptsConfidentialServiceAccountClient() {
        var planned = support.planCreate(createRequest("service", false, true, false));
        assertThat(planned.desiredState())
                .containsEntry("publicClient", false)
                .containsEntry("serviceAccountsEnabled", true);
    }

    @Test
    void plansAndAppliesEnabledTransition() {
        ClientRepresentation current = new ClientRepresentation();
        current.setEnabled(false);
        var planned = support.planEnabled(
                current, new ClientEnabledChangeRequest("target", "realm", "client", true, "actor", null));

        support.applyEnabled(current, planned.operations());

        assertThat(current.isEnabled()).isTrue();
        assertThat(support.compareEnabledDesired(current, planned.desiredState())).isEmpty();
    }

    @Test
    void rejectsNoOpEnabledTransition() {
        ClientRepresentation current = new ClientRepresentation();
        current.setEnabled(true);
        assertThatThrownBy(() -> support.planEnabled(
                        current,
                        new ClientEnabledChangeRequest("target", "realm", "client", true, "actor", null)))
                .isInstanceOf(McpException.class)
                .hasMessageContaining("No effective");
    }

    private static ClientCreateChangeRequest createRequest(
            String clientId, Boolean publicClient, Boolean serviceAccounts, Boolean enabled) {
        return new ClientCreateChangeRequest(
                "target",
                "realm",
                clientId,
                "Name",
                "Description",
                enabled,
                publicClient,
                null,
                null,
                serviceAccounts,
                "actor",
                null);
    }
}
