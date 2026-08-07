package io.github.keycloakmcp.assessment.profile;

import java.util.List;

public record AssessmentProfile(
        String name,
        List<String> rulePackIds) {
}
