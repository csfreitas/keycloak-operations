package io.github.keycloakmcp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.keycloakmcp.api.v1.ConfigurationReadCacheFilter;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigurationReadCacheFilterTest {
    @ParameterizedTest
    @ValueSource(strings = {"api/v1/configuration-reads", "/api/v1/configuration-reads",
            "api/v1/configuration-reads/example", "/api/v1/configuration-reads/example"})
    void scopedResponsesAlwaysDisableCaching(String path) {
        var headers = responseHeaders(path);
        assertThat(headers.getFirst("Cache-Control")).isEqualTo("no-store");
        assertThat(headers.get("Vary")).contains("Authorization");
    }

    @ParameterizedTest
    @ValueSource(strings = {"api/v1/targets", "/api/v1/targets", "api/v1/configuration-reads-other", "/"})
    void unrelatedResponsesAreUntouched(String path) {
        assertThat(responseHeaders(path)).isEmpty();
    }

    private MultivaluedHashMap<String, Object> responseHeaders(String path) {
        var request = mock(ContainerRequestContext.class);
        var response = mock(ContainerResponseContext.class);
        var uri = mock(UriInfo.class);
        var headers = new MultivaluedHashMap<String, Object>();
        when(request.getUriInfo()).thenReturn(uri);
        when(uri.getPath()).thenReturn(path);
        when(response.getHeaders()).thenReturn(headers);
        new ConfigurationReadCacheFilter().filter(request, response);
        return headers;
    }
}
