package io.github.keycloakmcp.collector.metrics;

import java.util.List;

import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.collector.EvidenceCollector;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Metrics evidence collector placeholder for 0.1.0 architecture.
 * Returns empty evidence until Prometheus/metrics scraping is wired in later releases.
 */
@ApplicationScoped
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
