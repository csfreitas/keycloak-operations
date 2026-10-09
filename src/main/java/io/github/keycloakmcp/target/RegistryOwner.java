package io.github.keycloakmcp.target;

/**
 * Provenance of the persisted target definition, not an operational permission or
 * the owner of its independently confirmed installation binding. Historical rows
 * remain unclassified until a separately approved ownership workflow exists.
 */
public enum RegistryOwner {
    LEGACY_UNCLASSIFIED,
    CONFIGURATION
}
