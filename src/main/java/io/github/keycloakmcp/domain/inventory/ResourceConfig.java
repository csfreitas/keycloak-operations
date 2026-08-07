package io.github.keycloakmcp.domain.inventory;

/**
 * CPU and memory resource requests/limits for a container or workload aggregate.
 */
public record ResourceConfig(
        String requestsCpu,
        String requestsMemory,
        String limitsCpu,
        String limitsMemory) {
}
