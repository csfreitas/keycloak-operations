package io.github.keycloakmcp.api.v1;

import io.github.keycloakmcp.service.platform.InstallationOnboardingService;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/targets/{targetId}/installation")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InstallationResource {
    @Inject InstallationOnboardingService service;
    @Inject SensitiveDataFilter filter;
    @GET public Object state(@PathParam("targetId") String targetId) { return filter.redact(service.state(targetId)); }
    @POST @Path("/discover") public Object discover(@PathParam("targetId") String targetId) { return filter.redact(service.discover(targetId)); }
    @POST @Path("/confirm") public Object confirm(@PathParam("targetId") String targetId, InstallationOnboardingService.Confirmation request) {
        return filter.redact(service.confirm(targetId, request));
    }
}
