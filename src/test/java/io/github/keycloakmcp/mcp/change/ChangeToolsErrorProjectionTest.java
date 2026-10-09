package io.github.keycloakmcp.mcp.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpError;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.service.change.ChangeManagementService;
import io.quarkiverse.mcp.server.ToolCallException;

class ChangeToolsErrorProjectionTest {

    private ChangeTools tools;

    @BeforeEach
    void setUp() {
        tools = new ChangeTools();
        tools.changeManagementService = mock(ChangeManagementService.class);
        tools.auditService = mock(AuditService.class);
        tools.metrics = mock(McpMetrics.class);
        tools.toolAuthorization = mock(ToolAuthorization.class);
        tools.sensitiveDataFilter = new SensitiveDataFilter(new ObjectMapper());
    }

    @ParameterizedTest
    @CsvSource({
            "password=synthetic-canary,password=[REDACTED]",
            "Bearer synthetic-canary,Bearer [REDACTED]",
            "https://operator:synthetic-canary@example.test,https://[REDACTED]@example.test",
            "eyJhbGciOiJub25lIn0.eyJzdWIiOiJjYW5hcnkifQ.synthetic,[REDACTED]"
    })
    void redactsDomainFailureWithoutRetainingRawCause(String message, String expectedMessage) {
        McpException failure = new McpException(McpError.of(ErrorCode.CHANGE_CONFLICT, message),
                new IllegalStateException("password=nested-cause-canary"));
        when(tools.changeManagementService.getChange("change-1")).thenThrow(failure);

        ToolCallException projected = invokeFailedRead();

        assertThat(projected).hasMessage("CHANGE_CONFLICT: " + expectedMessage).hasNoCause();
        assertThat(projected.getSuppressed()).isEmpty();
        assertThat(failure.getMessage()).isEqualTo(message);
        verifyFailedReadAudit();
    }

    @Test
    void retainsSafeDomainCodeAndExplanation() {
        when(tools.changeManagementService.getChange("change-1"))
                .thenThrow(McpException.of(ErrorCode.APPROVAL_REQUIRED, "Token lifespan change requires approval"));

        assertThat(invokeFailedRead())
                .hasMessage("APPROVAL_REQUIRED: Token lifespan change requires approval").hasNoCause();
        verifyFailedReadAudit();
    }

    @Test
    void domainFailureWithoutMessageUsesStableCodeFallback() {
        McpException failure = new McpException(McpError.of(ErrorCode.CHANGE_CONFLICT, "unused")) {
            @Override
            public String getMessage() {
                return null;
            }
        };
        when(tools.changeManagementService.getChange("change-1")).thenThrow(failure);

        assertThat(invokeFailedRead()).hasMessage("CHANGE_CONFLICT: CHANGE_CONFLICT").hasNoCause();
        verifyFailedReadAudit();
    }

    @Test
    void doesNotPassThroughPrebuiltToolFailureOrItsCause() {
        ToolCallException failure = new ToolCallException("CHANGE_CONFLICT: password=synthetic-canary",
                new IllegalStateException("password=nested-cause-canary"));
        failure.addSuppressed(new IllegalStateException("token=suppressed-canary"));
        when(tools.changeManagementService.getChange("change-1")).thenThrow(failure);

        ToolCallException projected = invokeFailedRead();

        assertThat(projected).isNotSameAs(failure)
                .hasMessage("CHANGE_CONFLICT: password=[REDACTED]").hasNoCause();
        assertThat(projected.getSuppressed()).isEmpty();
        verifyFailedReadAudit();
    }

    @Test
    void prebuiltToolFailureWithoutMessageUsesFixedFallback() {
        when(tools.changeManagementService.getChange("change-1"))
                .thenThrow(new ToolCallException((String) null));

        assertThat(invokeFailedRead()).hasMessage("INTERNAL_ERROR: change operation failed").hasNoCause();
        verifyFailedReadAudit();
    }

    @Test
    void unknownFailureKeepsFixedDiagnosticAndDropsCause() {
        when(tools.changeManagementService.getChange("change-1"))
                .thenThrow(new IllegalStateException("password=synthetic-canary"));

        assertThat(invokeFailedRead()).hasMessage("INTERNAL_ERROR: change operation failed").hasNoCause();
        verifyFailedReadAudit();
    }

    @Test
    void authorizationStillRunsBeforeTheServiceAndDenialIsSanitized() {
        doThrow(McpException.of(ErrorCode.POLICY_DENIED, "Operation denied: token=synthetic-canary"))
                .when(tools.toolAuthorization).assertReadOnlyOperation("keycloak_get_change");

        assertThat(invokeFailedRead()).hasMessage("POLICY_DENIED: Operation denied: token=[REDACTED]")
                .hasNoCause();
        verifyNoInteractions(tools.changeManagementService);
        verifyFailedReadAudit();
    }

    private ToolCallException invokeFailedRead() {
        return assertThrows(ToolCallException.class, () -> tools.keycloakGetChange("change-1"));
    }

    private void verifyFailedReadAudit() {
        verify(tools.toolAuthorization).assertReadOnlyOperation("keycloak_get_change");
        verify(tools.metrics).recordToolInvocation(eq("keycloak_get_change"), anyLong(), eq(false));
        verify(tools.auditService).logToolInvocation(eq("keycloak_get_change"), isNull(), isNull(), anyLong(), eq(false));
    }
}
