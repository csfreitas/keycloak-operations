package io.github.keycloakmcp.domain.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record McpError(ErrorCode code, String message, Map<String, Object> details) {

    public McpError {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public static McpError of(ErrorCode code, String message) {
        return new McpError(code, message, Map.of());
    }

    public static McpError of(ErrorCode code, String message, Map<String, Object> details) {
        return new McpError(code, message, details);
    }

    public static McpError notFound(ErrorCode code, String resourceType, String identifier) {
        return new McpError(
                code,
                resourceType + " not found: " + identifier,
                Map.of("resourceType", resourceType, "identifier", identifier));
    }

    public static McpError unavailable(ErrorCode code, String message) {
        return of(code, message);
    }

    public static McpError invalidArgument(String message) {
        return of(ErrorCode.INVALID_ARGUMENT, message);
    }

    public static McpError unsupported(String message) {
        return of(ErrorCode.UNSUPPORTED_CAPABILITY, message);
    }

    public static McpError internal(String message) {
        return of(ErrorCode.INTERNAL_ERROR, message);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code.name());
        map.put("message", message);
        if (!details.isEmpty()) {
            map.put("details", details);
        }
        return map;
    }
}
