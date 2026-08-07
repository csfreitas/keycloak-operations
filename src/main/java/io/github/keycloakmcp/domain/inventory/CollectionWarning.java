package io.github.keycloakmcp.domain.inventory;

/**
 * Warning produced during partial inventory collection.
 * When a resource type cannot be collected, a warning is emitted and collection continues.
 */
public record CollectionWarning(WarningCode code, String resource, String message) {

    public enum WarningCode {
        NOT_CONFIGURED,
        NOT_SUPPORTED,
        PERMISSION_DENIED,
        RESOURCE_NOT_FOUND,
        API_UNAVAILABLE,
        COLLECTION_FAILED
    }

    public static CollectionWarning permissionDenied(String resource, String details) {
        return new CollectionWarning(WarningCode.PERMISSION_DENIED, resource,
                "Permission denied collecting " + resource + (details != null ? ": " + details : ""));
    }

    public static CollectionWarning apiUnavailable(String resource, String details) {
        return new CollectionWarning(WarningCode.API_UNAVAILABLE, resource,
                "API unavailable for " + resource + (details != null ? ": " + details : ""));
    }

    public static CollectionWarning collectionFailed(String resource, String details) {
        return new CollectionWarning(WarningCode.COLLECTION_FAILED, resource,
                "Collection failed for " + resource + (details != null ? ": " + details : ""));
    }
}
