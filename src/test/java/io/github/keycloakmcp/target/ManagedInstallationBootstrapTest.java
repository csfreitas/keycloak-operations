package io.github.keycloakmcp.target;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import io.github.keycloakmcp.persistence.mapper.TargetPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.AuditRepository;
import io.github.keycloakmcp.persistence.repository.TargetRepository;
import jakarta.persistence.LockModeType;

class ManagedInstallationBootstrapTest {
    @Test void restartDoesNotOverwriteManagedBindingWithConfiguration() { check("iam", true); }
    @Test void namespaceChangeClearsManagedBindingAndIncrementsRevision() { check("changed", false); }

    private void check(String namespace, boolean preserve) {
        var mapper = new TargetPersistenceMapper();
        var binding = new KubernetesInstallationBinding("apps/v1", "Deployment", "rhbk", "uid");
        var before = target("iam", binding);
        var entity = mapper.toEntity(before);
        entity.registryOwner = RegistryOwner.CONFIGURATION;
        entity.installationManaged = true;
        entity.installationRevision = 3;
        var config = mock(ConfigurationTargetRegistry.class);
        when(config.list()).thenReturn(List.of(target(namespace, null)));
        var repository = mock(TargetRepository.class);
        when(repository.findById("bound", LockModeType.PESSIMISTIC_WRITE)).thenReturn(entity);
        new TargetBootstrapService(config, repository, mapper, mock(AuditRepository.class)).syncConfigTargetsToDatabase();
        assertThat(mapper.toDomain(entity).infrastructure().installation()).isEqualTo(preserve ? binding : null);
        assertThat(entity.installationManaged).isEqualTo(preserve);
        assertThat(entity.installationRevision).isEqualTo(preserve ? 3 : 4);
    }
    private Target target(String namespace, KubernetesInstallationBinding binding) {
        return new Target(TargetId.of("bound"), "Bound", TargetType.RHBK, TargetEnvironment.TEST, true,
                new KeycloakTargetConfiguration("http://localhost:8080", "master", "assessor", "ref"),
                new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "cluster", namespace, "infra-ref", binding), null, Map.of());
    }
}
