package io.github.keycloakmcp.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.keycloakmcp.adapter.infrastructure.InfrastructureClientFactory;
import io.github.keycloakmcp.config.DiscoveryConfig;

@ExtendWith(MockitoExtension.class)
class EnvironmentDiscoveryTest {

    @Mock
    private DiscoveryConfig discoveryConfig;

    @Mock
    private DiscoveryConfig.Kubernetes kubernetes;

    @Mock
    private DiscoveryConfig.OpenShift openshift;

    @Mock
    private InfrastructureClientFactory clientFactory;

    private EnvironmentDiscovery discovery;

    @BeforeEach
    void setUp() {
        when(discoveryConfig.kubernetes()).thenReturn(kubernetes);
        when(discoveryConfig.openshift()).thenReturn(openshift);
        discovery = new EnvironmentDiscovery(discoveryConfig, clientFactory);
    }

    @Test
    void whenKubernetesAndOpenShiftDisabledReturnsUnknown() {
        when(kubernetes.enabled()).thenReturn(false);
        when(openshift.enabled()).thenReturn(false);

        EnvironmentInfo info = discovery.discover();

        assertThat(info.runtime()).isEqualTo(RuntimeType.UNKNOWN);
        assertThat(info.confidence()).isEqualTo(DetectionConfidence.UNKNOWN);
        assertThat(info.platform()).isEqualTo("unknown");
        assertThat(info.namespace()).isNull();
        assertThat(info.evidence())
                .anyMatch(e -> e.contains("discovery.openshift.enabled=false"))
                .anyMatch(e -> e.contains("discovery.kubernetes.enabled=false"));
    }
}
