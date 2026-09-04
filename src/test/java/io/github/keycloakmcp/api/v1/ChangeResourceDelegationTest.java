package io.github.keycloakmcp.api.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.domain.change.ChangeRecord;
import io.github.keycloakmcp.domain.change.ClientSecurityChangeRequest;
import io.github.keycloakmcp.domain.change.ClientUrlChangeRequest;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.change.ChangeManagementService;

class ChangeResourceDelegationTest {

    @Test
    void typedClientUrlEndpointDelegatesToSharedApplicationService() {
        ChangeManagementService service = mock(ChangeManagementService.class);
        SensitiveDataFilter filter = mock(SensitiveDataFilter.class);
        ChangeRecord expected = mock(ChangeRecord.class);
        ClientUrlChangeRequest request = new ClientUrlChangeRequest(
                "target-a",
                "realm-a",
                "client-a",
                List.of("https://app.example/callback"),
                null,
                "operator",
                "idem-1");
        when(service.planClientUrlUpdate(request)).thenReturn(expected);
        when(filter.redact(expected)).thenReturn(expected);
        ChangeResource resource = new ChangeResource();
        resource.changeManagementService = service;
        resource.sensitiveDataFilter = filter;

        assertThat(resource.planClientUrls(request)).isSameAs(expected);
        verify(service).planClientUrlUpdate(request);
    }

    @Test
    void typedClientSecurityEndpointDelegatesToSharedApplicationService() {
        ChangeManagementService service = mock(ChangeManagementService.class);
        SensitiveDataFilter filter = mock(SensitiveDataFilter.class);
        ChangeRecord expected = mock(ChangeRecord.class);
        ClientSecurityChangeRequest request = new ClientSecurityChangeRequest(
                "target-a",
                "realm-a",
                "client-a",
                "S256",
                true,
                false,
                false,
                false,
                true,
                "operator",
                "idem-security-1");
        when(service.planClientSecurityUpdate(request)).thenReturn(expected);
        when(filter.redact(expected)).thenReturn(expected);
        ChangeResource resource = new ChangeResource();
        resource.changeManagementService = service;
        resource.sensitiveDataFilter = filter;

        assertThat(resource.planClientSecurity(request)).isSameAs(expected);
        verify(service).planClientSecurityUpdate(request);
    }
}
