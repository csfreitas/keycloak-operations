package io.github.keycloakmcp.adapter.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpError;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetType;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

class StableAdminApiAdapterDiagnosticTest {
    private static final String CANARY = "diagnostic-canary-password";
    private final KeycloakClientFactory factory = mock(KeycloakClientFactory.class);
    private final StableAdminApiAdapter adapter = new StableAdminApiAdapter(factory, mock(McpMetrics.class));
    private final Target target = new Target(TargetId.of("test"), "test", TargetType.KEYCLOAK,
            TargetEnvironment.DEV, true,
            new KeycloakTargetConfiguration("http://localhost", "master", "client", "ref"),
            null, null, Map.of());

    @ParameterizedTest
    @MethodSource("providerFailures")
    void scopedErrorsRetainCodesButNotBodiesIdentifiersOrNestedCauses(RuntimeException source, ErrorCode expected) {
        when(factory.getClient(target)).thenThrow(source);
        try (var scope = CollectionBudget.open("test", 30_000)) {
            McpException failure = catchThrowableOfType(() -> adapter.listClients(target, CANARY, false), McpException.class);
            assertSafe(failure, expected);
        }
    }

    @ParameterizedTest
    @MethodSource("missingResources")
    void scopedMissingResourceKeepsItsSpecificCode(String resource, ErrorCode expected) {
        when(factory.getClient(target)).thenThrow(new NotFoundException(CANARY));
        try (var scope = CollectionBudget.open("test", 30_000)) {
            McpException failure = catchThrowableOfType(() -> {
                switch (resource) {
                    case "realm" -> adapter.getRealm(target, CANARY);
                    case "client" -> adapter.getClient(target, CANARY, CANARY);
                    case "user" -> adapter.getUser(target, CANARY, CANARY);
                    case "group" -> adapter.getGroup(target, CANARY, CANARY);
                    case "role" -> adapter.getRealmRole(target, CANARY, CANARY);
                    default -> throw new AssertionError("Unexpected test resource");
                }
            }, McpException.class);
            assertSafe(failure, expected);
        }
    }

    @Test
    void existingStructuredScopedFailureDoesNotRetainRawDetailsOrSuppressedCauses() {
        McpException source = new McpException(McpError.of(ErrorCode.EVIDENCE_COLLECTION_FAILED,
                CANARY, Map.of("body", CANARY)), new IllegalStateException(CANARY));
        source.addSuppressed(new IllegalStateException(CANARY));
        when(factory.getClient(target)).thenThrow(source);
        try (var scope = CollectionBudget.open("test", 30_000)) {
            assertSafe(catchThrowableOfType(() -> adapter.getServerInfo(target), McpException.class),
                    ErrorCode.EVIDENCE_COLLECTION_FAILED);
        }
    }

    @Test
    void followingOrdinaryCallKeepsExistingExceptionContract() {
        ProcessingException source = new ProcessingException(CANARY);
        when(factory.getClient(target)).thenThrow(source);
        try (var scope = CollectionBudget.open("test", 30_000)) {
            assertSafe(catchThrowableOfType(() -> adapter.getServerInfo(target), McpException.class),
                    ErrorCode.KEYCLOAK_UNAVAILABLE);
        }
        McpException ordinary = catchThrowableOfType(() -> adapter.getServerInfo(target), McpException.class);
        assertThat(ordinary).hasCause(source).hasMessage("Keycloak Admin API is unavailable for getServerInfo");
    }

    private static Stream<Arguments> providerFailures() {
        return Stream.of(
                Arguments.of(new ProcessingException(CANARY), ErrorCode.KEYCLOAK_UNAVAILABLE),
                Arguments.of(new IllegalStateException(CANARY), ErrorCode.KEYCLOAK_UNAVAILABLE),
                Arguments.of(new NotAuthorizedException(CANARY), ErrorCode.AUTHENTICATION_FAILED),
                Arguments.of(failure(401), ErrorCode.AUTHENTICATION_FAILED),
                Arguments.of(failure(403), ErrorCode.AUTHORIZATION_FAILED),
                Arguments.of(failure(404), ErrorCode.REALM_NOT_FOUND),
                Arguments.of(failure(429), ErrorCode.KEYCLOAK_UNAVAILABLE),
                Arguments.of(failure(500), ErrorCode.KEYCLOAK_UNAVAILABLE));
    }

    private static Stream<Arguments> missingResources() {
        return Stream.of(
                Arguments.of("realm", ErrorCode.REALM_NOT_FOUND),
                Arguments.of("client", ErrorCode.CLIENT_NOT_FOUND),
                Arguments.of("user", ErrorCode.USER_NOT_FOUND),
                Arguments.of("group", ErrorCode.GROUP_NOT_FOUND),
                Arguments.of("role", ErrorCode.ROLE_NOT_FOUND));
    }

    private static WebApplicationException failure(int status) {
        return new WebApplicationException(CANARY,
                new IllegalStateException(CANARY), Response.status(status).entity(CANARY).build());
    }

    private static void assertSafe(McpException failure, ErrorCode expected) {
        assertThat(failure).isNotNull().hasNoCause();
        assertThat(failure.getCode()).isEqualTo(expected);
        assertThat(failure.getMessage()).doesNotContain(CANARY);
        assertThat(failure.getError().details()).isEmpty();
        assertThat(failure.getSuppressed()).isEmpty();
    }
}
