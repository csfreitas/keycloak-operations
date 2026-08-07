package io.github.keycloakmcp.domain.inventory;

import java.util.Map;

/**
 * Topology distribution of Keycloak pods across zones and nodes.
 */
public record TopologyInfo(
        /** Zone name → pod count. Empty if zone labels unavailable. */
        Map<String, Integer> podsByZone,
        /** Node name → pod count. */
        Map<String, Integer> podsByNode,
        /** Number of distinct zones with at least one pod. */
        int zoneCount) {
}
