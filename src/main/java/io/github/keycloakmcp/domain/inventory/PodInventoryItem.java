package io.github.keycloakmcp.domain.inventory;

/**
 * Summary of a single Keycloak pod.
 */
public record PodInventoryItem(
        String name,
        String nodeName,
        String zone,
        boolean ready,
        int restartCount,
        boolean oomKilled) {
}
