package io.github.keycloakmcp.collector.vm;

import java.util.List;

import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.collector.EvidenceCollector;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * VM evidence collector placeholder for 0.1.0 architecture.
 * Returns empty evidence (not an error) until VM probes are implemented.
 */
@ApplicationScoped
public class VmEvidenceCollector implements EvidenceCollector {

    @Override
    public String source() {
        return "vm";
    }

    @Override
    public List<Evidence> collect(Target target) {
        return List.of();
    }
}
