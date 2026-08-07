package io.github.keycloakmcp.observability.metrics;

/**
 * Escapes values embedded into PromQL label matchers.
 * Prevents breaking out of quoted label values.
 */
public final class PromQlEscaper {

    private PromQlEscaper() {
    }

    /**
     * Escape a label value for use inside double quotes in PromQL.
     */
    public static String labelValue(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Build {@code label="escaped"} matcher fragment.
     */
    public static String labelEq(String label, String value) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label name required");
        }
        if (!label.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("invalid label name: " + label);
        }
        return label + "=\"" + labelValue(value) + "\"";
    }
}
