package io.github.keycloakmcp.assessment.engine;

import java.util.Optional;

public interface Rule {

    String id();

    String title();

    String category();

    Severity severity();

    boolean applies(EvidenceContext context);

    Optional<Finding> evaluate(EvidenceContext context);
}
