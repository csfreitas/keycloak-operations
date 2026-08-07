package io.github.keycloakmcp.target;

import java.util.Locale;

public enum TargetType {
    KEYCLOAK,
    RHBK;

    public static TargetType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("target type must not be blank");
        }
        return TargetType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    public String productLabel() {
        return name();
    }
}
