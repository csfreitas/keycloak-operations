package io.github.keycloakmcp.mcp.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.change.ChangeRecord;
import io.github.keycloakmcp.domain.change.ClientUrlChangeRequest;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.service.change.ChangeManagementService;

class ChangeToolsClientUrlsTest {

    @Test
    void typedClientUrlToolDelegatesToSharedApplicationService() {
        ChangeManagementService service = mock(ChangeManagementService.class);
        ChangeRecord expected = mock(ChangeRecord.class);
        when(service.planClientUrlUpdate(any(ClientUrlChangeRequest.class))).thenReturn(expected);
        ChangeTools tools = new ChangeTools();
        tools.changeManagementService = service;
        tools.auditService = mock(AuditService.class);
        tools.metrics = mock(McpMetrics.class);
        tools.toolAuthorization = mock(ToolAuthorization.class);

        ChangeRecord result = tools.keycloakPlanUpdateClientUrls(
                "target-a",
                "realm-a",
                "client-a",
                List.of("https://app.example/callback"),
                null,
                "operator",
                "idem-1");

        assertThat(result).isSameAs(expected);
        verify(tools.toolAuthorization).assertReadOnlyOperation("keycloak_plan_update_client_urls");
        verify(service).planClientUrlUpdate(new ClientUrlChangeRequest(
                "target-a",
                "realm-a",
                "client-a",
                List.of("https://app.example/callback"),
                null,
                "operator",
                "idem-1"));
    }
}
