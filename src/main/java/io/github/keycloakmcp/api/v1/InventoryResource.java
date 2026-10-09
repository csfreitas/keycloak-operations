package io.github.keycloakmcp.api.v1;

import io.github.keycloakmcp.discovery.EnvironmentDiscovery;
import io.github.keycloakmcp.discovery.EnvironmentInfo;
import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
import io.github.keycloakmcp.security.ReadMetadataProjection;
import io.github.keycloakmcp.service.platform.InventoryService;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/targets/{targetId}")
@Produces(MediaType.APPLICATION_JSON)
public class InventoryResource {

    @Inject
    InventoryService inventoryService;

    @Inject
    EnvironmentDiscovery environmentDiscovery;

    @Inject
    TargetResolver targetResolver;

    @Inject
    ReadMetadataProjection readProjection;

    @Inject
    TargetAuthorizationService targetAuthorization;

    @GET
    @Path("/inventory")
    public InfrastructureInventory inventory(@PathParam("targetId") String targetId) {
        return readProjection.inventory(inventoryService.collect(targetId));
    }

    @GET
    @Path("/environment")
    public EnvironmentInfo environment(@PathParam("targetId") String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        return readProjection.project(environmentDiscovery.discover(target), "targetId");
    }

    @GET
    @Path("/topology")
    public Object topology(@PathParam("targetId") String targetId) {
        InfrastructureInventory inventory = inventoryService.collect(targetId);
        return readProjection.topology(inventory.topology());
    }
}
