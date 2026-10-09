package io.github.keycloakmcp.api.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;

class RegistryPreflightCacheFilterTest {
    private final RegistryPreflightCacheFilter filter = new RegistryPreflightCacheFilter();

    @ParameterizedTest
    @ValueSource(strings = {
            "api/v1/registry", "/api/v1/registry", "api/v1/registry/", "/api/v1/registry/",
            "api/v1/registry/targets/preflight", "/api/v1/registry/targets/preflight"
    })
    void registryResponsesCannotBeReusedAcrossAuthorizationContexts(String path) {
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(response.getHeaders()).thenReturn(headers);

        filter.filter(request(path), response);

        assertThat(headers.get("Cache-Control")).containsExactly("no-store");
        assertThat(headers.get("Vary")).contains("Authorization");
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 400, 401, 403, 404, 500, 503})
    void cacheProtectionAlsoAppliesToRejectedAndFailedResponses(int status) {
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.putSingle("Cache-Control", "public, max-age=600");
        headers.add("Vary", "Origin");
        when(response.getHeaders()).thenReturn(headers);
        when(response.getStatus()).thenReturn(status);

        filter.filter(request("/api/v1/registry/targets/preflight"), response);

        assertThat(headers.get("Cache-Control")).containsExactly("no-store");
        assertThat(headers.get("Vary")).contains("Origin", "Authorization");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "api/v1/registry-other", "/api/v1/registry-other/targets/preflight",
            "api/v1/registries", "/api/v1/targets", "api/v1/configuration-reads", "mcp", ""
    })
    void unrelatedPathsAreNotChanged(String path) {
        ContainerResponseContext response = mock(ContainerResponseContext.class);

        filter.filter(request(path), response);

        verifyNoInteractions(response);
    }

    @Test
    void filterIsRegisteredForErrorsAsWellAsResourceSuccesses() {
        assertThat(RegistryPreflightCacheFilter.class.isAnnotationPresent(Provider.class)).isTrue();
    }

    private static ContainerRequestContext request(String path) {
        UriInfo uri = mock(UriInfo.class);
        when(uri.getPath()).thenReturn(path);
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        when(request.getUriInfo()).thenReturn(uri);
        return request;
    }
}
