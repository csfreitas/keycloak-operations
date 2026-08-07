package io.github.keycloakmcp.api.v1;

import java.util.List;

import io.github.keycloakmcp.domain.platform.TargetOverview;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.platform.TargetOverviewService;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetRegistry;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/targets")
@Produces(MediaType.APPLICATION_JSON)
public class TargetResource {

    @Inject
    TargetRegistry targetRegistry;

    @Inject
    TargetResolver targetResolver;

    @Inject
    TargetAuthorizationService targetAuthorization;

    @Inject
    TargetOverviewService targetOverviewService;

    @Inject
    SensitiveDataFilter sensitiveDataFilter;

    @GET
    public List<?> list() {
        return sensitiveDataFilter.redact(targetRegistry.list().stream()
                .filter(t -> {
                    try {
                        targetAuthorization.assertAllowed(t, TargetPermission.READ);
                        return true;
                    } catch (RuntimeException e) {
                        return false;
                    }
                })
                .map(this::toStatus)
                .toList());
    }

    @GET
    @Path("/{targetId}")
    public Object get(@PathParam("targetId") String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        return sensitiveDataFilter.redact(toStatus(target));
    }

    @GET
    @Path("/{targetId}/status")
    public Object status(@PathParam("targetId") String targetId) {
        TargetOverview overview = targetOverviewService.overview(targetId);
        return sensitiveDataFilter.redact(overview);
    }

    private java.util.Map<String, Object> toStatus(Target target) {
        return java.util.Map.of(
                "id", target.id().value(),
                "displayName", target.displayName(),
                "productType", target.type().name(),
                "environment", target.environment().name(),
                "enabled", target.enabled(),
                "keycloakUrl", target.keycloak().url(),
                "tags", target.tags());
    }
}
