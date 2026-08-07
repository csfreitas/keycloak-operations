package io.github.keycloakmcp.collector.kubernetes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.collector.EvidenceCollector;
import io.github.keycloakmcp.config.DiscoveryConfig;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class KubernetesEvidenceCollector implements EvidenceCollector {

    private static final Logger LOG = Logger.getLogger(KubernetesEvidenceCollector.class);

    private final DiscoveryConfig discoveryConfig;

    @Inject
    public KubernetesEvidenceCollector(DiscoveryConfig discoveryConfig) {
        this.discoveryConfig = discoveryConfig;
    }

    @Override
    public String source() {
        return "kubernetes";
    }

    @Override
    public List<Evidence> collect(Target target) {
        if (!discoveryConfig.kubernetes().enabled()
                || target.infrastructureTypeOrNone() != InfrastructureType.KUBERNETES) {
            return List.of();
        }

        String targetId = target.id().value();
        Instant now = Instant.now();
        List<Evidence> evidence = new ArrayList<>();
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            int namespaceCount = client.namespaces().list().getItems().size();
            evidence.add(new Evidence(targetId, source(), "cluster", "kubernetes.namespace.count", namespaceCount, now));
            String namespace = client.getNamespace();
            if (namespace != null) {
                evidence.add(new Evidence(targetId, source(), "cluster", "kubernetes.namespace.current", namespace, now));
            }
            var version = client.getKubernetesVersion();
            if (version != null && version.getGitVersion() != null) {
                evidence.add(new Evidence(targetId, source(), "cluster", "kubernetes.version", version.getGitVersion(), now));
            }
        } catch (RuntimeException e) {
            LOG.debugf(e, "Kubernetes evidence collection skipped");
            return List.of();
        }
        return List.copyOf(evidence);
    }
}
