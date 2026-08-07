package io.github.keycloakmcp.collector.openshift;

import java.util.List;

import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.collector.EvidenceCollector;
import io.github.keycloakmcp.collector.infrastructure.InfrastructureEvidenceCollector;
import io.github.keycloakmcp.target.Target;

/**
 * @deprecated Use {@link InfrastructureEvidenceCollector} via {@code AssessmentEvidenceService}.
 * Removed from CDI to avoid duplicate infrastructure evidence collection.
 */
@Deprecated(forRemoval = true)
public class OpenShiftEvidenceCollector implements EvidenceCollector {

    @Override
    public String source() {
        return "openshift";
    }

    @Override
    public List<Evidence> collect(Target target) {
        return List.of();
    }
}
