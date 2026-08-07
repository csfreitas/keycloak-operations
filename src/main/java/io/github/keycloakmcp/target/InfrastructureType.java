package io.github.keycloakmcp.target;

import java.util.Locale;

public enum InfrastructureType {
    OPENSHIFT,
    KUBERNETES,
    VM,
    NONE;

    public static InfrastructureType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        return InfrastructureType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
