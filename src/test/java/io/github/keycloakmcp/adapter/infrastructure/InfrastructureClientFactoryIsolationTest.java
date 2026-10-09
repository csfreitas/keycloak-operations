package io.github.keycloakmcp.adapter.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.github.keycloakmcp.credential.CredentialProvider;
import io.github.keycloakmcp.credential.InfrastructureCredentials;
import io.github.keycloakmcp.target.InfrastructureTargetConfiguration;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetType;

@EnableKubernetesMockClient(crud = true)
class InfrastructureClientFactoryIsolationTest {

    KubernetesMockServer server;

    private CredentialProvider credentialProvider;
    private InfrastructureClientFactory factory;

    @BeforeEach
    void setUp() {
        credentialProvider = mock(CredentialProvider.class);
        factory = new InfrastructureClientFactory(credentialProvider);
    }

    @AfterEach
    void tearDown() {
        factory.shutdown();
    }

    @Test
    void differentTargetsResolveDifferentCredentialRefs() {
        String url = server.url("/");
        when(credentialProvider.getInfrastructureCredentials("infra-a"))
                .thenReturn(InfrastructureCredentials.token("token-a", url, null, true));
        when(credentialProvider.getInfrastructureCredentials("infra-b"))
                .thenReturn(InfrastructureCredentials.token("token-b", url, null, true));

        Target targetA = target("target-a", "ns-a", "infra-a");
        Target targetB = target("target-b", "ns-b", "infra-b");

        Optional<ClusterClient> clientA = factory.resolve(targetA);
        Optional<ClusterClient> clientB = factory.resolve(targetB);

        assertThat(clientA).isPresent();
        assertThat(clientB).isPresent();
        assertThat(clientA.get().namespace()).isEqualTo("ns-a");
        assertThat(clientB.get().namespace()).isEqualTo("ns-b");
        assertThat(clientA.get()).isNotSameAs(clientB.get());
        assertThat(clientA.get().kubernetes().getConfiguration().getOauthToken()).isEqualTo("token-a");
        assertThat(clientB.get().kubernetes().getConfiguration().getOauthToken()).isEqualTo("token-b");
        assertThat(factory.resolve(targetA).orElseThrow()).isSameAs(clientA.get());
    }

    @Test
    void noneInfrastructureReturnsEmpty() {
        Target target = new Target(
                TargetId.of("plain"),
                "Plain",
                TargetType.KEYCLOAK,
                TargetEnvironment.DEV,
                true,
                new KeycloakTargetConfiguration("http://kc", "master", "client", "lab-a"),
                new InfrastructureTargetConfiguration(InfrastructureType.NONE, null, null, null),
                null,
                java.util.Map.of());
        assertThat(factory.resolve(target)).isEmpty();
    }

    @Test
    void missingCredentialReferenceDoesNotCreateAnAmbientClient() {
        assertThat(factory.resolve(target("missing-ref", "ns", null))).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(credentialProvider);
    }

    @Test
    void tokenRotationInvalidatesCachedClient() {
        when(credentialProvider.getInfrastructureCredentials("rotating"))
                .thenReturn(InfrastructureCredentials.token("fixture", server.url("/"), null, true))
                .thenReturn(InfrastructureCredentials.token("fixture-rotated", server.url("/"), null, true));
        Target target = target("rotation", "ns", "rotating");
        assertThat(factory.resolve(target).orElseThrow()).isNotSameAs(factory.resolve(target).orElseThrow());
    }

    private static Target target(String id, String namespace, String credentialRef) {
        return new Target(
                TargetId.of(id),
                id,
                TargetType.KEYCLOAK,
                TargetEnvironment.DEV,
                true,
                new KeycloakTargetConfiguration("http://kc", "master", "client", "lab-a"),
                new InfrastructureTargetConfiguration(
                        InfrastructureType.KUBERNETES, "cluster-" + id, namespace, credentialRef),
                null,
                java.util.Map.of());
    }
}
