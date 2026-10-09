package io.github.keycloakmcp.adapter.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.github.keycloakmcp.target.InfrastructureType;

class DefaultClusterClientTest {
    @Test
    void legacyTypeIsOnlyConfiguredIntentAndCannotTriggerImplicitDiscovery() {
        var transport = mock(KubernetesClient.class);
        var cluster = new DefaultClusterClient(transport, "explicit-scope");
        assertThat(cluster.type()).isEqualTo(InfrastructureType.NONE);
        assertThat(cluster.openshift()).isEmpty();
        cluster.setTypeHint(InfrastructureType.OPENSHIFT);
        assertThat(cluster.type()).isEqualTo(InfrastructureType.OPENSHIFT);
        cluster.setTypeHint(InfrastructureType.KUBERNETES);
        assertThat(cluster.type()).isEqualTo(InfrastructureType.KUBERNETES);
        assertThat(cluster.openshift()).isEmpty();
        cluster.setTypeHint(null);
        assertThat(cluster.type()).isEqualTo(InfrastructureType.NONE);
        verifyNoInteractions(transport);
    }
}
