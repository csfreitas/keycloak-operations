package io.github.keycloakmcp.api.v1;

import java.io.InputStream;

import io.github.keycloakmcp.service.registry.RegistryPreflightService;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/registry/targets/preflight")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class RegistryPreflightResource {
    private final RegistryPreflightService service;

    @Inject
    public RegistryPreflightResource(RegistryPreflightService service) { this.service = service; }

    @POST
    @Blocking
    public Response preflight(InputStream body) {
        // The service authorizes before deserializing or inspecting local registry metadata.
        var result = service.preflight(body);
        int status = switch (result.validation()) {
            case LOCALLY_VALID -> 200;
            case REJECTED -> 400;
            case INCONCLUSIVE -> 503;
        };
        return Response.status(status).entity(result).build();
    }
}
