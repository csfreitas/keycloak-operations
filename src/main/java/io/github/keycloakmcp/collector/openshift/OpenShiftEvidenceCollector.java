package io.github.keycloakmcp.collector.openshift;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.collector.EvidenceCollector;
import io.github.keycloakmcp.config.DiscoveryConfig;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OpenShiftEvidenceCollector implements EvidenceCollector {

    private static final Logger LOG = Logger.getLogger(OpenShiftEvidenceCollector.class);

    private final DiscoveryConfig discoveryConfig;

    @Inject
    public OpenShiftEvidenceCollector(DiscoveryConfig discoveryConfig) {
        this.discoveryConfig = discoveryConfig;
    }

    @Override
    public String source() {
        return "openshift";
    }

    @Override
    public List<Evidence> collect(Target target) {
        if (!discoveryConfig.openshift().enabled()
                || target.infrastructureTypeOrNone() != InfrastructureType.OPENSHIFT) {
            return List.of();
        }

        String targetId = target.id().value();
        Instant now = Instant.now();
        List<Evidence> evidence = new ArrayList<>();
        try (OpenShiftClient client = new KubernetesClientBuilder().build().adapt(OpenShiftClient.class)) {
            if (client == null) {
                return List.of();
            }
            int projectCount = client.projects().list().getItems().size();
            evidence.add(new Evidence(targetId, source(), "cluster", "openshift.project.count", projectCount, now));
            String namespace = client.getNamespace();
            if (namespace != null) {
                evidence.add(new Evidence(targetId, source(), "cluster", "openshift.namespace.current", namespace, now));
            }
            var version = client.getKubernetesVersion();
            if (version != null && version.getGitVersion() != null) {
                evidence.add(new Evidence(targetId, source(), "cluster", "openshift.version", version.getGitVersion(), now));
            }
        } catch (RuntimeException e) {
            LOG.debugf(e, "OpenShift evidence collection skipped");
            return List.of();
        }
        return List.copyOf(evidence);
    }
}
