package io.github.keycloakmcp.collector.metrics;

import java.util.List;

import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.collector.EvidenceCollector;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.inject.Vetoed;

/**
 * Metrics evidence collector placeholder.
 * {@link Vetoed} so it is not pulled into CDI {@code Instance<EvidenceCollector>} loops.
 */
@Vetoed
public class MetricsEvidenceCollector implements EvidenceCollector {

    @Override
    public String source() {
        return "metrics";
    }

    @Override
    public List<Evidence> collect(Target target) {
        return List.of();
    }
}
