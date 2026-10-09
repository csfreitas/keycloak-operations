package io.github.keycloakmcp.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class KubernetesInstallationBindingTest {
    @Test
    void supportsExplicitWorkloadAndVersionedOperatorIdentities() {
        for (String kind : new String[] {"Deployment", "StatefulSet"}) {
            assertThat(new KubernetesInstallationBinding("apps/v1", kind, "rhbk", "uid-123").kind()).isEqualTo(kind);
        }
        assertThat(new KubernetesInstallationBinding("k8s.keycloak.org/v2alpha1", "Keycloak", "rhbk", "uid-123").name()).isEqualTo("rhbk");
    }

    @Test
    void rejectsMissingOrUnsupportedIdentity() {
        assertThatIllegalArgumentException().isThrownBy(() -> new KubernetesInstallationBinding("apps/v1", "Pod", "rhbk", "uid"));
        assertThatIllegalArgumentException().isThrownBy(() -> new KubernetesInstallationBinding(null, "Deployment", "rhbk", "uid"));
        assertThatIllegalArgumentException().isThrownBy(() -> new KubernetesInstallationBinding("apps/v1", "Deployment", "../other", "uid"));
        assertThatIllegalArgumentException().isThrownBy(() -> new KubernetesInstallationBinding("apps/v1", "Deployment", "rhbk", ""));
    }

    @Test
    void boundInstallationRequiresExplicitClusterNamespaceAndCredentials() {
        var binding = new KubernetesInstallationBinding("apps/v1", "Deployment", "rhbk", "uid");
        assertThatIllegalArgumentException().isThrownBy(() -> new InfrastructureTargetConfiguration(InfrastructureType.VM, "c", "ns", "ref", binding));
        assertThatIllegalArgumentException().isThrownBy(() -> new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, null, "ns", "ref", binding));
        assertThatIllegalArgumentException().isThrownBy(() -> new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "c", " ", "ref", binding));
        assertThatIllegalArgumentException().isThrownBy(() -> new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "c", "ns", null, binding));
        assertThat(new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "c", "ns", "ref").installation()).isNull();
    }
}
