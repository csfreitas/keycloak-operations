package io.github.keycloakmcp.api.v1;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/** Do not let a browser/intermediary reuse another principal's catalogue or observation. */
@Provider
public class ConfigurationReadCacheFilter implements ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        String path = request.getUriInfo().getPath();
        // REST implementations can retain the leading slash in UriInfo.getPath().
        if (path.startsWith("/")) path = path.substring(1);
        if ("api/v1/configuration-reads".equals(path) || path.startsWith("api/v1/configuration-reads/")) {
            response.getHeaders().putSingle("Cache-Control", "no-store");
            response.getHeaders().add("Vary", "Authorization");
        }
    }
}
