package io.github.keycloakmcp.assessment.profile;

import java.util.List;

/**
 * Named assessment profile selecting rule packs and expected evidence sources.
 */
public record AssessmentProfile(
        String name,
        String description,
        List<String> products,
        List<String> runtimes,
        List<String> rulePackIds,
        List<String> requiredEvidenceSources,
        List<String> optionalEvidenceSources) {

    public AssessmentProfile {
        description = description == null ? "" : description;
        products = products == null ? List.of() : List.copyOf(products);
        runtimes = runtimes == null ? List.of() : List.copyOf(runtimes);
        rulePackIds = rulePackIds == null ? List.of() : List.copyOf(rulePackIds);
        requiredEvidenceSources =
                requiredEvidenceSources == null ? List.of() : List.copyOf(requiredEvidenceSources);
        optionalEvidenceSources =
                optionalEvidenceSources == null ? List.of() : List.copyOf(optionalEvidenceSources);
    }

    /** Backward-compatible constructor used by older call sites / tests. */
    public AssessmentProfile(String name, List<String> rulePackIds) {
        this(name, "", List.of(), List.of(), rulePackIds, List.of("keycloak"), List.of());
    }
}
