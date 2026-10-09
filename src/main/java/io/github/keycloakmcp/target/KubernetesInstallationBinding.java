package io.github.keycloakmcp.target;

/** Approved root identity within the parent target's explicit connection and namespace. */
public record KubernetesInstallationBinding(String apiVersion, String kind, String name, String uid) {
    public KubernetesInstallationBinding {
        boolean supported = ("Deployment".equals(kind) || "StatefulSet".equals(kind)) && "apps/v1".equals(apiVersion)
                || "Keycloak".equals(kind) && apiVersion != null && apiVersion.matches("k8s\\.keycloak\\.org/v[0-9]+((alpha|beta)[0-9]+)?");
        if (!supported || apiVersion.length() > 128 || name == null || name.length() > 253 || !name.matches("[a-z0-9]([-a-z0-9.]*[a-z0-9])?")
                || uid == null || uid.length() > 128 || !uid.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("A supported API/kind and explicit installation name/UID are required");
        }
    }
}
