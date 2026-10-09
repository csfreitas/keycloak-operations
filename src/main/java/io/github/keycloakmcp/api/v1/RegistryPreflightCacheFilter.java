package io.github.keycloakmcp.api.v1;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/** Local registry existence is administrative metadata, including on error responses. */
@Provider
public class RegistryPreflightCacheFilter implements ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        String path = request.getUriInfo().getPath();
        if (path.startsWith("/")) path = path.substring(1);
        if ("api/v1/registry".equals(path) || path.startsWith("api/v1/registry/")) {
            response.getHeaders().putSingle("Cache-Control", "no-store");
            response.getHeaders().add("Vary", "Authorization");
        }
    }
}
