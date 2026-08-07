package io.github.keycloakmcp.assessment.engine.rules;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.keycloakmcp.assessment.engine.EvidenceContext;
import io.github.keycloakmcp.assessment.engine.Finding;
import io.github.keycloakmcp.assessment.engine.FindingStatus;
import io.github.keycloakmcp.assessment.engine.Rule;
import io.github.keycloakmcp.assessment.engine.Severity;

public class MinimumReplicasRule implements Rule {

    public static final String EVIDENCE_KEY = "deployment.replicas";
    public static final String FINDING_ID = "KC-OCP-HA-001";

    @Override
    public String id() {
        return FINDING_ID;
    }

    @Override
    public String title() {
        return "Keycloak deployment should run with at least 2 replicas";
    }

    @Override
    public String category() {
        return "high-availability";
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }

    @Override
    public boolean applies(EvidenceContext context) {
        return context != null && context.hasKey(EVIDENCE_KEY);
    }

    @Override
    public Optional<Finding> evaluate(EvidenceContext context) {
        EvidenceContext.OptionalIntResult replicas = context.getInt(EVIDENCE_KEY);
        if (!replicas.present()) {
            return Optional.empty();
        }
        if (replicas.value() >= 2) {
            return Optional.empty();
        }
        return Optional.of(new Finding(
                context.targetId(),
                FINDING_ID,
                title(),
                category(),
                severity(),
                FindingStatus.OPEN,
                "Deployment replica count is " + replicas.value() + ", which is below the recommended minimum of 2.",
                Map.of(EVIDENCE_KEY, replicas.value()),
                "A single replica cannot tolerate pod or node failure without downtime.",
                "Scale the Keycloak/RHBK deployment to at least 2 replicas for high availability.",
                List.of("https://www.keycloak.org/high-availability/introduction")));
    }
}
