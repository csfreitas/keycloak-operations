package io.github.keycloakmcp.api.v1;

import io.github.keycloakmcp.domain.platform.TargetOverview;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.platform.TargetOverviewService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/targets/{targetId}/overview")
@Produces(MediaType.APPLICATION_JSON)
public class TargetOverviewResource {

    @Inject
    TargetOverviewService targetOverviewService;

    @Inject
    SensitiveDataFilter sensitiveDataFilter;

    @GET
    public TargetOverview overview(@PathParam("targetId") String targetId) {
        return sensitiveDataFilter.redact(targetOverviewService.overview(targetId));
    }
}
