package io.github.keycloakmcp.domain.platform;

/**
 * How an operation was triggered (assessment, health check, snapshot, etc.).
 */
public enum TriggerType {
    MCP,
    WEB,
    API,
    SCHEDULED,
    SYSTEM
}
