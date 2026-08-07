package io.github.keycloakmcp.target;

import java.util.Locale;

public enum TargetEnvironment {
    DEV,
    TEST,
    HML,
    STAGING,
    PRD,
    UNKNOWN;

    public static TargetEnvironment parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        try {
            return TargetEnvironment.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
