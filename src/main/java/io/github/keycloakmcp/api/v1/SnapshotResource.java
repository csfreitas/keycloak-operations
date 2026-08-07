package io.github.keycloakmcp.api.v1;

import java.util.List;

import io.github.keycloakmcp.domain.platform.EnvironmentChange;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.domain.platform.SnapshotDetail;
import io.github.keycloakmcp.domain.platform.SnapshotSummary;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.platform.EnvironmentChangeService;
import io.github.keycloakmcp.service.platform.SnapshotService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/targets/{targetId}/snapshots")
@Produces(MediaType.APPLICATION_JSON)
public class SnapshotResource {

    @Inject
    SnapshotService snapshotService;

    @Inject
    EnvironmentChangeService environmentChangeService;

    @Inject
    SensitiveDataFilter sensitiveDataFilter;

    @POST
    public SnapshotSummary create(@PathParam("targetId") String targetId) {
        return sensitiveDataFilter.redact(snapshotService.create(targetId));
    }

    @GET
    public PageResult<SnapshotSummary> list(
            @PathParam("targetId") String targetId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return sensitiveDataFilter.redact(snapshotService.list(targetId, page, size));
    }

    @GET
    @Path("/changes")
    public List<EnvironmentChange> changes(
            @PathParam("targetId") String targetId,
            @QueryParam("from") String fromSnapshotId,
            @QueryParam("to") String toSnapshotId) {
        return sensitiveDataFilter.redact(
                environmentChangeService.compare(targetId, fromSnapshotId, toSnapshotId));
    }

    @GET
    @Path("/latest")
    public SnapshotDetail latest(@PathParam("targetId") String targetId) {
        return sensitiveDataFilter.redact(snapshotService.latestDetail(targetId)
                .orElseThrow(() -> io.github.keycloakmcp.domain.error.McpException.invalidArgument(
                        "no snapshot for target: " + targetId)));
    }

    @GET
    @Path("/{snapshotId}")
    public SnapshotDetail get(
            @PathParam("targetId") String targetId,
            @PathParam("snapshotId") String snapshotId) {
        return sensitiveDataFilter.redact(snapshotService.getDetail(targetId, snapshotId));
    }
}
