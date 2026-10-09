package io.github.keycloakmcp.api.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.service.registry.RegistryPreflightResult;
import io.github.keycloakmcp.service.registry.RegistryPreflightResult.Issue;
import io.github.keycloakmcp.service.registry.RegistryPreflightResult.Status;
import io.github.keycloakmcp.service.registry.RegistryPreflightService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

class RegistryPreflightResourceTest {
    private final RegistryPreflightService service = mock(RegistryPreflightService.class);
    private final RegistryPreflightResource resource = new RegistryPreflightResource(service);

    @ParameterizedTest
    @MethodSource("resultStatuses")
    void mapsTheSharedServiceResultWithoutReadingOrRewritingTheRequest(Status status, int httpStatus) {
        InputStream body = unreadableBody();
        RegistryPreflightResult result = result(status);
        when(service.preflight(body)).thenReturn(result);

        try (Response response = resource.preflight(body)) {
            assertThat(response.getStatus()).isEqualTo(httpStatus);
            assertThat(response.getEntity()).isSameAs(result);
        }

        verify(service).preflight(body);
        verifyNoMoreInteractions(service);
    }

    @Test
    void authorizationFailureIsNotConvertedIntoAnApprovedOrValidationResponse() {
        InputStream body = unreadableBody();
        McpException denied = McpException.authorizationFailed("Registry preflight is not authorized");
        when(service.preflight(body)).thenThrow(denied);

        assertThatThrownBy(() -> resource.preflight(body)).isSameAs(denied);

        verify(service).preflight(body);
        verifyNoMoreInteractions(service);
    }

    @Test
    void exposesOnlyTheNamedJsonPostPreflightBoundary() throws NoSuchMethodException {
        assertThat(RegistryPreflightResource.class.getAnnotation(Path.class).value())
                .isEqualTo("/api/v1/registry/targets/preflight");
        var method = RegistryPreflightResource.class.getMethod("preflight", InputStream.class);
        assertThat(method.isAnnotationPresent(POST.class)).isTrue();
        Consumes consumes = method.getAnnotation(Consumes.class);
        if (consumes == null) consumes = RegistryPreflightResource.class.getAnnotation(Consumes.class);
        Produces produces = method.getAnnotation(Produces.class);
        if (produces == null) produces = RegistryPreflightResource.class.getAnnotation(Produces.class);
        assertThat(consumes).isNotNull();
        assertThat(consumes.value()).containsExactly(MediaType.APPLICATION_JSON);
        assertThat(produces).isNotNull();
        assertThat(produces.value()).containsExactly(MediaType.APPLICATION_JSON);
    }

    private static Stream<Arguments> resultStatuses() {
        return Stream.of(
                Arguments.of(Status.LOCALLY_VALID, 200),
                Arguments.of(Status.REJECTED, 400),
                Arguments.of(Status.INCONCLUSIVE, 503));
    }

    private static RegistryPreflightResult result(Status status) {
        List<Issue> issues = switch (status) {
            case LOCALLY_VALID -> List.of();
            case REJECTED -> List.of(new Issue("targetId", "ALREADY_EXISTS"));
            case INCONCLUSIVE -> List.of(new Issue("registry", "REGISTRY_UNAVAILABLE"));
        };
        return new RegistryPreflightResult("0.1.0", status, issues, false, false,
                "NOT_TESTED", "NOT_TESTED", "NOT_CHECKED", true);
    }

    private static InputStream unreadableBody() {
        return new InputStream() {
            @Override
            public int read() {
                throw new AssertionError("The resource must delegate the unread request to its service");
            }
        };
    }
}
