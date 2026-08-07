package io.github.keycloakmcp.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Guardrail: Web UI OpenShift manifests must not mount a privileged ServiceAccount
 * or grant Kubernetes / Secret / Prometheus access.
 */
class UiDeployManifestTest {

    @Test
    void uiDeploymentHasNoAssessorAccess() throws Exception {
        String yaml = Files.readString(Path.of("deploy/openshift/100-ui-deployment.yaml"));
        assertThat(yaml).contains("keycloak-operations-ui");
        assertThat(yaml).contains("automountServiceAccountToken: false");
        assertThat(yaml).doesNotContain("keycloak-mcp-assessor");
        assertThat(yaml).doesNotContain("ClusterRole");
        assertThat(yaml.toLowerCase()).doesNotContain("secretref");
        assertThat(yaml).doesNotContain("DISCOVERY_KUBERNETES_ENABLED");
        assertThat(yaml).contains("0.7.0-SNAPSHOT");
    }
}
