package io.github.keycloakmcp.domain.realm;

public record RealmDetails(
        String realm,
        String displayName,
        boolean enabled,
        boolean registrationAllowed,
        boolean resetPasswordAllowed,
        boolean editUsernameAllowed,
        boolean bruteForceProtected,
        String sslRequired,
        boolean loginWithEmailAllowed,
        boolean duplicateEmailsAllowed,
        boolean internationalizationEnabled) {
}
