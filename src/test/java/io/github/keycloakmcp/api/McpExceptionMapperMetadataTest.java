package io.github.keycloakmcp.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpError;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import jakarta.ws.rs.core.Response;

class McpExceptionMapperMetadataTest {

    private final McpExceptionMapper mapper = new McpExceptionMapper();

    @BeforeEach
    void setUp() {
        mapper.sensitiveDataFilter = new SensitiveDataFilter(new ObjectMapper());
    }

    @ParameterizedTest
    @MethodSource("credentialMessages")
    void redactsRecognizableCredentialsWithoutReturningDetailsOrCause(String message, String expectedMessage) {
        McpException failure = new McpException(
                McpError.of(ErrorCode.CHANGE_CONFLICT, message, Map.of("detail", "password=details-canary")),
                new IllegalStateException("password=cause-canary"));

        try (Response response = mapper.toResponse(failure)) {
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(response.getEntity()).isEqualTo(Map.of(
                    "code", "CHANGE_CONFLICT", "message", expectedMessage));
        }
        assertThat(failure.getMessage()).isEqualTo(message);
    }

    @ParameterizedTest
    @CsvSource({
            "CLIENT_NOT_FOUND,404", "INVALID_ARGUMENT,400", "CHANGE_CONFLICT,409",
            "APPROVAL_REQUIRED,403", "AUTHENTICATION_FAILED,401", "TARGET_NOT_AUTHORIZED,403",
            "KEYCLOAK_UNAVAILABLE,503", "WRITE_NOT_SUPPORTED,501", "INTERNAL_ERROR,500"
    })
    void preservesStatusCodeAndSafeDiagnostic(ErrorCode code, int status) {
        String message = "Token lifespan policy requires an approved change";
        try (Response response = mapper.toResponse(McpException.of(code, message))) {
            assertThat(response.getStatus()).isEqualTo(status);
            assertThat(response.getEntity()).isEqualTo(Map.of("code", code.name(), "message", message));
        }
    }

    @Test
    void retainsCodeFallbackWhenExceptionHasNoMessage() {
        McpException failure = new McpException(McpError.of(ErrorCode.INVALID_ARGUMENT, "unused")) {
            @Override
            public String getMessage() {
                return null;
            }
        };

        try (Response response = mapper.toResponse(failure)) {
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getEntity()).isEqualTo(Map.of(
                    "code", "INVALID_ARGUMENT", "message", "INVALID_ARGUMENT"));
        }
    }

    private static Stream<Arguments> credentialMessages() {
        return Stream.of(
                Arguments.of("Conflict: password=synthetic-canary", "Conflict: password=[REDACTED]"),
                Arguments.of("Conflict: secret=\"synthetic canary\"", "Conflict: secret=\"[REDACTED]\""),
                Arguments.of("Conflict: Bearer synthetic-canary", "Conflict: Bearer [REDACTED]"),
                Arguments.of("Conflict: https://operator:synthetic-canary@example.test/path",
                        "Conflict: https://[REDACTED]@example.test/path"),
                Arguments.of("Conflict: eyJhbGciOiJub25lIn0.eyJzdWIiOiJjYW5hcnkifQ.synthetic",
                        "Conflict: [REDACTED]"),
                Arguments.of("Conflict: -----BEGIN PRIVATE KEY-----\nsynthetic-canary\n-----END PRIVATE KEY-----",
                        "Conflict: [REDACTED]"));
    }
}
