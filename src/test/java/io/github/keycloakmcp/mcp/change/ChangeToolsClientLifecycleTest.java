package io.github.keycloakmcp.mcp.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.change.ChangeRecord;
import io.github.keycloakmcp.domain.change.ClientCreateChangeRequest;
import io.github.keycloakmcp.domain.change.ClientEnabledChangeRequest;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.service.change.ChangeManagementService;

class ChangeToolsClientLifecycleTest {

    private ChangeManagementService service;
    private ChangeTools tools;
    private ChangeRecord expected;

    @BeforeEach
    void setUp() {
        service = mock(ChangeManagementService.class);
        expected = mock(ChangeRecord.class);
        tools = new ChangeTools();
        tools.changeManagementService = service;
        tools.auditService = mock(AuditService.class);
        tools.metrics = mock(McpMetrics.class);
        tools.toolAuthorization = mock(ToolAuthorization.class);
    }

    @Test
    void createToolDelegatesTypedSecretFreeRequest() {
        when(service.planClientCreate(any(ClientCreateChangeRequest.class))).thenReturn(expected);

        assertThat(tools.keycloakPlanCreateClient(
                "target", "realm", "client", "Name", "Description",
                false, true, true, false, false, "actor", "idem"))
                .isSameAs(expected);

        verify(tools.toolAuthorization).assertReadOnlyOperation("keycloak_plan_create_client");
        verify(service).planClientCreate(new ClientCreateChangeRequest(
                "target", "realm", "client", "Name", "Description",
                false, true, true, false, false, "actor", "idem"));
    }

    @Test
    void enabledToolDelegatesTypedRequest() {
        when(service.planClientEnabledUpdate(any(ClientEnabledChangeRequest.class))).thenReturn(expected);

        assertThat(tools.keycloakPlanSetClientEnabled(
                "target", "realm", "client", true, "actor", "idem"))
                .isSameAs(expected);

        verify(tools.toolAuthorization).assertReadOnlyOperation("keycloak_plan_set_client_enabled");
        verify(service).planClientEnabledUpdate(new ClientEnabledChangeRequest(
                "target", "realm", "client", true, "actor", "idem"));
    }
}
