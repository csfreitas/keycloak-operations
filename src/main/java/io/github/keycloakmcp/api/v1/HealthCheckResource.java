package io.github.keycloakmcp.api.v1;

import io.github.keycloakmcp.domain.platform.HealthCheckDetail;
import io.github.keycloakmcp.domain.platform.HealthCheckSummary;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.platform.HealthCheckService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/targets/{targetId}/health-checks")
@Produces(MediaType.APPLICATION_JSON)
public class HealthCheckResource {

    @Inject
    HealthCheckService healthCheckService;

    @Inject
    SensitiveDataFilter sensitiveDataFilter;

    @POST
    public HealthCheckSummary run(@PathParam("targetId") String targetId) {
        return sensitiveDataFilter.redact(healthCheckService.run(targetId, TriggerType.API));
    }

    @GET
    public PageResult<HealthCheckSummary> list(
            @PathParam("targetId") String targetId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return sensitiveDataFilter.redact(healthCheckService.list(targetId, page, size));
    }

    @GET
    @Path("/latest")
    public HealthCheckDetail latest(@PathParam("targetId") String targetId) {
        return sensitiveDataFilter.redact(healthCheckService.latestDetail(targetId)
                .orElseThrow(() -> io.github.keycloakmcp.domain.error.McpException.invalidArgument(
                        "no health check for target: " + targetId)));
    }

    @GET
    @Path("/{healthCheckId}")
    public HealthCheckDetail get(
            @PathParam("targetId") String targetId,
            @PathParam("healthCheckId") String healthCheckId) {
        return sensitiveDataFilter.redact(healthCheckService.get(targetId, healthCheckId));
    }
}
