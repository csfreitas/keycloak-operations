package io.github.keycloakmcp.domain.inventory;

/**
 * Warning produced during partial inventory collection.
 * When a resource type cannot be collected, a warning is emitted and collection continues.
 */
public record CollectionWarning(WarningCode code, String resource, String message) {

    private static final java.util.Set<String> RESOURCES = java.util.Set.of(
            "infrastructure", "installation", "workload", "nodes", "node-zones", "openshift-config",
            "clusterversion", "discovery", "cluster-api-version", "pods", "pod-zones", "hpa", "pdb", "networking", "resources", "probes", "collection-budget");

    public CollectionWarning {
        code = code == null ? WarningCode.COLLECTION_FAILED : code;
        // Warning text is public inventory data. Never retain provider bodies, URLs or exception messages.
        resource = RESOURCES.contains(resource == null ? "" : resource) ? resource : null;
        message = switch (code) {
            case NOT_CONFIGURED -> "Infrastructure collection is not configured";
            case NOT_SUPPORTED -> "Resource collection is not supported";
            case PERMISSION_DENIED -> "Resource collection was denied";
            case RESOURCE_NOT_FOUND -> "Requested resource was not found";
            case API_UNAVAILABLE -> "Resource API is unavailable";
            case COLLECTION_FAILED -> "Resource collection is incomplete";
            case BINDING_REQUIRED -> "An explicit installation binding is required";
            case BINDING_MISMATCH -> "Installation identity requires reconfirmation";
            case AMBIGUOUS_RESOURCE -> "Resource association is ambiguous";
            case OPERATION_BUDGET_EXCEEDED -> "Collection operation time budget was exceeded";
            case OPERATION_INTERRUPTED -> "Collection operation was interrupted";
        };
    }

    public enum WarningCode {
        NOT_CONFIGURED,
        NOT_SUPPORTED,
        PERMISSION_DENIED,
        RESOURCE_NOT_FOUND,
        API_UNAVAILABLE,
        COLLECTION_FAILED,
        BINDING_REQUIRED,
        BINDING_MISMATCH,
        AMBIGUOUS_RESOURCE,
        OPERATION_BUDGET_EXCEEDED,
        OPERATION_INTERRUPTED
    }

    public static CollectionWarning permissionDenied(String resource, String details) {
        return new CollectionWarning(WarningCode.PERMISSION_DENIED, resource, null);
    }

    public static CollectionWarning apiUnavailable(String resource, String details) {
        return new CollectionWarning(WarningCode.API_UNAVAILABLE, resource, null);
    }

    public static CollectionWarning collectionFailed(String resource, String details) {
        return new CollectionWarning(WarningCode.COLLECTION_FAILED, resource, null);
    }
}
