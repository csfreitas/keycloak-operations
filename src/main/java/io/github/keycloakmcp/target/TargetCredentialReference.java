package io.github.keycloakmcp.target;

/**
 * Opaque reference to a credential entry in {@code mcp.credentials.*}.
 */
public record TargetCredentialReference(String ref) {

    public TargetCredentialReference {
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("credential ref must not be blank");
        }
        ref = ref.trim();
    }
}
