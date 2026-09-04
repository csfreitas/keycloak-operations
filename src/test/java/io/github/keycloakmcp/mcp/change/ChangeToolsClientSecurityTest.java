package io.github.keycloakmcp.mcp.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.change.ChangeRecord;
import io.github.keycloakmcp.domain.change.ClientSecurityChangeRequest;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.service.change.ChangeManagementService;

class ChangeToolsClientSecurityTest {

    @Test
    void typedClientSecurityToolDelegatesToSharedApplicationService() {
        ChangeManagementService service = mock(ChangeManagementService.class);
        ChangeRecord expected = mock(ChangeRecord.class);
        when(service.planClientSecurityUpdate(any(ClientSecurityChangeRequest.class))).thenReturn(expected);
        ChangeTools tools = new ChangeTools();
        tools.changeManagementService = service;
        tools.auditService = mock(AuditService.class);
        tools.metrics = mock(McpMetrics.class);
        tools.toolAuthorization = mock(ToolAuthorization.class);

        ChangeRecord result = tools.keycloakPlanUpdateClientSecurity(
                "target-a", "realm-a", "client-a", "S256", true, false, false, false, true,
                "operator", "idem-security-1");

        assertThat(result).isSameAs(expected);
        verify(tools.toolAuthorization).assertReadOnlyOperation("keycloak_plan_update_client_security");
        verify(service).planClientSecurityUpdate(new ClientSecurityChangeRequest(
                "target-a", "realm-a", "client-a", "S256", true, false, false, false, true,
                "operator", "idem-security-1"));
    }
}
