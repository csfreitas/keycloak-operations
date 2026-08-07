package io.github.keycloakmcp.api.v1;

import java.util.Optional;

import io.github.keycloakmcp.assessment.engine.Finding;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.platform.AssessmentHistoryService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/targets/{targetId}/findings")
@Produces(MediaType.APPLICATION_JSON)
public class FindingResource {

    @Inject
    AssessmentHistoryService assessmentHistoryService;

    @Inject
    SensitiveDataFilter sensitiveDataFilter;

    @GET
    public PageResult<Finding> list(
            @PathParam("targetId") String targetId,
            @QueryParam("lifecycleStatus") String lifecycleStatus,
            @QueryParam("severity") String severity,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return sensitiveDataFilter.redact(assessmentHistoryService.findings(
                targetId,
                Optional.ofNullable(lifecycleStatus),
                Optional.ofNullable(severity),
                page,
                size));
    }
}
