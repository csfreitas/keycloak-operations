package io.github.keycloakmcp.domain.realm;

public record RealmSummary(
        String realm,
        String displayName,
        boolean enabled) {
}
