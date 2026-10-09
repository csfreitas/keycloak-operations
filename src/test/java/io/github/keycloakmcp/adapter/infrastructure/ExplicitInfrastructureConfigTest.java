package io.github.keycloakmcp.adapter.infrastructure;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import io.github.keycloakmcp.credential.InfrastructureCredentials;
import io.github.keycloakmcp.domain.error.McpException;

class ExplicitInfrastructureConfigTest {
    @TempDir Path directory;

    @Test @ResourceLock("java.lang.System.properties")
    void tokenConfigurationIgnoresAmbientEndpointCredentialsAndTls() {
        String[] keys = {"kubernetes.master", "kubernetes.auth.basic.username", "kubernetes.trust.certificates"};
        String[] previous = java.util.Arrays.stream(keys).map(System::getProperty).toArray(String[]::new);
        try {
            System.setProperty(keys[0], "https://unrelated.invalid");
            System.setProperty(keys[1], "unrelated-user");
            System.setProperty(keys[2], "true");
            var config = ExplicitInfrastructureConfig.resolve(
                    InfrastructureCredentials.token("fixture", "https://approved.invalid", null, false), "approved-ns");
            assertThat(config.getMasterUrl()).startsWith("https://approved.invalid");
            assertThat(config.getUsername()).isNull();
            assertThat(config.isTrustCerts()).isFalse();
            assertThat(config.getOauthToken()).isEqualTo("fixture");
            assertThat(config.getAutoConfigure()).isFalse();
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (previous[i] == null) System.clearProperty(keys[i]); else System.setProperty(keys[i], previous[i]);
            }
        }
    }

    @Test void missingEndpointAndNamespaceFailClosed() {
        assertThatThrownBy(() -> ExplicitInfrastructureConfig.resolve(
                InfrastructureCredentials.token("fixture", null, null, false), "ns")).isInstanceOf(McpException.class);
        assertThatThrownBy(() -> ExplicitInfrastructureConfig.resolve(
                InfrastructureCredentials.token("fixture", "https://approved.invalid", null, false), null)).isInstanceOf(McpException.class);
    }

    @Test void unsafeServerUrisAreRejected() {
        for (String uri : new String[] {"http://remote.invalid", "https://user:secret@approved.invalid", "https://approved.invalid?token=secret", "file:///tmp/test"}) {
            assertThatThrownBy(() -> ExplicitInfrastructureConfig.resolve(
                    InfrastructureCredentials.token("fixture", uri, null, false), "ns")).isInstanceOf(McpException.class)
                    .hasMessageNotContaining("secret");
        }
    }

    @Test void inClusterUsesOnlySuppliedMountFixtureAndFixedServiceEndpoint() throws Exception {
        Files.writeString(directory.resolve("token"), "mounted-fixture-token\n");
        Files.writeString(directory.resolve("ca.crt"), "fixture-ca");
        var config = ExplicitInfrastructureConfig.inCluster("approved-ns", directory);
        assertThat(config.getMasterUrl()).startsWith("https://kubernetes.default.svc");
        assertThat(config.getOauthToken()).isEqualTo("mounted-fixture-token");
        assertThat(config.getNamespace()).isEqualTo("approved-ns");
        assertThat(config.isTrustCerts()).isFalse();
        assertThat(config.getAutoConfigure()).isFalse();
    }

    @Test void missingServiceAccountNeverFallsBack() {
        assertThatThrownBy(() -> ExplicitInfrastructureConfig.inCluster("ns", directory)).isInstanceOf(McpException.class);
    }

    @Test void explicitKubeconfigUsesItsSelectedContext() throws Exception {
        var config = ExplicitInfrastructureConfig.resolve(kubeconfig("token: fixture-token"), "approved-ns");
        assertThat(config.getMasterUrl()).startsWith("https://approved.invalid");
        assertThat(config.getOauthToken()).isEqualTo("fixture-token");
        assertThat(config.getNamespace()).isEqualTo("approved-ns");
        assertThat(config.getAutoConfigure()).isFalse();
    }

    @Test void kubeconfigExecProviderIsRejectedBeforeExecution() throws Exception {
        var credentials = kubeconfig("exec:\n      apiVersion: client.authentication.k8s.io/v1\n      command: forbidden-fixture-command");
        assertThatThrownBy(() -> ExplicitInfrastructureConfig.resolve(credentials, "ns")).isInstanceOf(McpException.class)
                .hasMessageNotContaining("forbidden-fixture-command");
    }

    @Test void malformedKubeconfigDoesNotExposeItsContents() throws Exception {
        Path file = directory.resolve("invalid.yaml");
        Files.writeString(file, "[secret-fixture-malformed");
        assertThatThrownBy(() -> ExplicitInfrastructureConfig.resolve(InfrastructureCredentials.kubeconfig(file.toString()), "ns"))
                .isInstanceOf(McpException.class).hasMessageNotContaining("secret-fixture-malformed");
    }

    @Test void kubeconfigExternalCredentialFilesAreRejected() throws Exception {
        var credentials = kubeconfig("client-certificate: /unapproved/cert.pem\n    client-key: /unapproved/key.pem");
        assertThatThrownBy(() -> ExplicitInfrastructureConfig.resolve(credentials, "ns")).isInstanceOf(McpException.class);
    }

    @Test void kubeconfigMissingSelectedContextDoesNotSelectAnother() throws Exception {
        var credentials = kubeconfig("token: fixture-token");
        Path file = Path.of(credentials.kubeconfigPath());
        Files.writeString(file, Files.readString(file).replace("current-context: approved", "current-context: missing"));
        assertThatThrownBy(() -> ExplicitInfrastructureConfig.resolve(credentials, "ns")).isInstanceOf(McpException.class);
    }

    private InfrastructureCredentials kubeconfig(String user) throws Exception {
        Path file = directory.resolve("config.yaml");
        Files.writeString(file, """
                apiVersion: v1
                kind: Config
                current-context: approved
                clusters:
                - name: fixture
                  cluster:
                    server: https://approved.invalid
                contexts:
                - name: approved
                  context:
                    cluster: fixture
                    user: reader
                users:
                - name: reader
                  user:
                    %s
                """.formatted(user));
        return InfrastructureCredentials.kubeconfig(file.toString());
    }
}
