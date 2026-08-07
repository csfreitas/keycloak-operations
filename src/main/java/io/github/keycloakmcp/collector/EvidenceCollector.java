package io.github.keycloakmcp.collector;

import java.util.List;

import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.target.Target;

public interface EvidenceCollector {

    String source();

    List<Evidence> collect(Target target);
}
