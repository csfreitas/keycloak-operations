package io.github.keycloakmcp.api.v1;

import java.util.Optional;

import io.github.keycloakmcp.domain.platform.AuditEventSummary;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.platform.AuditQueryService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/audit")
@Produces(MediaType.APPLICATION_JSON)
public class AuditResource {

    @Inject
    AuditQueryService auditQueryService;

    @Inject
    SensitiveDataFilter sensitiveDataFilter;

    @GET
    public PageResult<AuditEventSummary> list(
            @QueryParam("targetId") String targetId,
            @QueryParam("source") String source,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return sensitiveDataFilter.redact(auditQueryService.list(
                Optional.ofNullable(targetId),
                Optional.ofNullable(source),
                page,
                size));
    }
}
