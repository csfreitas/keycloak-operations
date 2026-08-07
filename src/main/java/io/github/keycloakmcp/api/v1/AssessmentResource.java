package io.github.keycloakmcp.api.v1;

import io.github.keycloakmcp.assessment.engine.AssessmentResult;
import io.github.keycloakmcp.domain.platform.AssessmentRunSummary;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.platform.AssessmentHistoryService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/targets/{targetId}/assessments")
@Produces(MediaType.APPLICATION_JSON)
public class AssessmentResource {

    @Inject
    AssessmentHistoryService assessmentHistoryService;

    @Inject
    SensitiveDataFilter sensitiveDataFilter;

    @POST
    public AssessmentResult run(
            @PathParam("targetId") String targetId,
            @QueryParam("profile") String profile) {
        // Blank/null → AssessmentEngine uses assessment.default-profile (keycloak-production).
        return sensitiveDataFilter.redact(
                assessmentHistoryService.runAndPersist(targetId, profile, TriggerType.API));
    }

    @GET
    public PageResult<AssessmentRunSummary> list(
            @PathParam("targetId") String targetId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return sensitiveDataFilter.redact(assessmentHistoryService.list(targetId, page, size));
    }

    @GET
    @Path("/{assessmentId}")
    public AssessmentRunSummary get(
            @PathParam("targetId") String targetId,
            @PathParam("assessmentId") String assessmentId) {
        return sensitiveDataFilter.redact(assessmentHistoryService.get(targetId, assessmentId));
    }
}
