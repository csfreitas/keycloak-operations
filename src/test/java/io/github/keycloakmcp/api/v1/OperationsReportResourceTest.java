package io.github.keycloakmcp.api.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.domain.report.OperationsReport;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.platform.OperationsReportService;

class OperationsReportResourceTest {

    @Test
    void delegatesToSharedApplicationService() {
        OperationsReportService service = mock(OperationsReportService.class);
        OperationsReport expected = mock(OperationsReport.class);
        when(service.generate("target-a", "keycloak-production", "15m", TriggerType.API)).thenReturn(expected);
        OperationsReportResource resource = new OperationsReportResource();
        resource.operationsReportService = service;
        resource.sensitiveDataFilter = new SensitiveDataFilter(new ObjectMapper().findAndRegisterModules());

        OperationsReport result = resource.generate("target-a", "keycloak-production", "15m");

        assertThat(result).isNotNull();
        verify(service).generate("target-a", "keycloak-production", "15m", TriggerType.API);
    }
}
