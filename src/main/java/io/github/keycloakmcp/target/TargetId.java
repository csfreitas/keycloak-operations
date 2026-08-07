package io.github.keycloakmcp.target;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object for a configured MCP target identifier.
 * Preferred form is lowercase kebab-case; allowed characters are {@code [a-zA-Z0-9._-]}.
 */
public record TargetId(String value) {

    private static final Pattern ALLOWED = Pattern.compile("[a-zA-Z0-9._-]+");

    public TargetId {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("target id must not be blank");
        }
        if (!ALLOWED.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "target id must match [a-zA-Z0-9._-]: " + trimmed);
        }
        value = trimmed;
    }

    public static TargetId of(String value) {
        return new TargetId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
