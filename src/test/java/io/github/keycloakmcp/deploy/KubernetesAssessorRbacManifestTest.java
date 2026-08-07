package io.github.keycloakmcp.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Guardrail: Kubernetes assessor manifests must not grant Secret get/list.
 */
class KubernetesAssessorRbacManifestTest {

    @Test
    void kubernetesManifestDoesNotGrantSecretAccess() throws Exception {
        Path path = Path.of("deploy/kubernetes/deployment.yaml");
        String yaml = Files.readString(path);
        assertThat(yaml).doesNotContain("resources: [\"namespaces\", \"nodes\", \"pods\", \"services\", \"configmaps\", \"secrets\"]");
        // Role/ClusterRole blocks should not list secrets as a resource
        assertThat(yaml.toLowerCase()).doesNotContain("\"secrets\"");
        assertThat(yaml).contains("containerPort: 8081");
        assertThat(yaml).contains("containerPort: 9001");
        assertThat(yaml).contains("0.6.1-SNAPSHOT");
        assertThat(yaml).contains("monitoring.coreos.com");
    }
}
