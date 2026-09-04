package io.github.keycloakmcp.service.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;

import io.github.keycloakmcp.domain.change.ClientSecurityChangeRequest;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;

class ClientSecuritySettingsChangeSupportTest {

    private ClientSecuritySettingsChangeSupport support;

    @BeforeEach
    void setUp() {
        support = new ClientSecuritySettingsChangeSupport();
    }

    @Test
    void plansNormalizedTypedSecuritySettings() {
        ClientRepresentation current = client(false, false);

        var planned = support.plan(current, request(" s256 ", true, null, false, true, false));

        assertThat(planned.desiredState())
                .containsEntry("pkceCodeChallengeMethod", "S256")
                .containsEntry("standardFlowEnabled", true)
                .containsEntry("directAccessGrantsEnabled", false)
                .containsEntry("serviceAccountsEnabled", true)
                .containsEntry("publicClient", false);
        assertThat(planned.operations()).extracting("property")
                .containsExactly("pkceCodeChallengeMethod", "standardFlowEnabled", "serviceAccountsEnabled");
    }

    @Test
    void appliesOnlyAllowlistedSettingsAndClearsPkceWithNone() {
        ClientRepresentation current = client(false, false);
        current.getAttributes().put("pkce.code.challenge.method", "S256");
        current.setRootUrl("https://preserved.example");
        var planned = support.plan(current, request("NONE", true, true, null, null, null));

        support.applyToRepresentation(current, planned.operations());

        assertThat(current.getAttributes())
                .containsKey("pkce.code.challenge.method")
                .containsEntry("pkce.code.challenge.method", "");
        assertThat(current.isStandardFlowEnabled()).isTrue();
        assertThat(current.isImplicitFlowEnabled()).isTrue();
        assertThat(current.getRootUrl()).isEqualTo("https://preserved.example");
        assertThat(support.compareDesired(current, planned.desiredState())).isEmpty();
    }

    @Test
    void rejectsEmptyAndUnsupportedPkceRequests() {
        ClientRepresentation current = client(false, false);
        assertInvalid(() -> support.plan(current, request(null, null, null, null, null, null)));
        assertInvalid(() -> support.plan(current, request("plain", null, null, null, null, null)));
    }

    @Test
    void rejectsServiceAccountsForEffectivePublicClient() {
        ClientRepresentation current = client(true, false);

        assertInvalid(() -> support.plan(current, request(null, null, null, null, true, null)));
        assertInvalid(() -> support.plan(current, request(null, null, null, null, true, true)));
    }

    @Test
    void acceptsAtomicConversionToConfidentialServiceAccountClient() {
        ClientRepresentation current = client(true, false);

        var planned = support.plan(current, request(null, null, null, null, true, false));

        assertThat(planned.operations()).extracting("property")
                .containsExactly("serviceAccountsEnabled", "publicClient");
    }

    private static ClientRepresentation client(boolean publicClient, boolean serviceAccounts) {
        ClientRepresentation client = new ClientRepresentation();
        client.setAttributes(new HashMap<>());
        client.setPublicClient(publicClient);
        client.setServiceAccountsEnabled(serviceAccounts);
        client.setStandardFlowEnabled(false);
        client.setImplicitFlowEnabled(false);
        client.setDirectAccessGrantsEnabled(false);
        return client;
    }

    private static ClientSecurityChangeRequest request(
            String pkce,
            Boolean standard,
            Boolean implicit,
            Boolean direct,
            Boolean serviceAccounts,
            Boolean publicClient) {
        return new ClientSecurityChangeRequest(
                "target", "realm", "client", pkce, standard, implicit, direct,
                serviceAccounts, publicClient, "actor", null);
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(McpException.class)
                .satisfies(error -> assertThat(((McpException) error).getCode())
                        .isEqualTo(ErrorCode.INVALID_ARGUMENT));
    }
}
