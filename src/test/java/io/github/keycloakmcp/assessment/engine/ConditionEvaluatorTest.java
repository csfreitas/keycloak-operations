package io.github.keycloakmcp.assessment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ConditionEvaluatorTest {

    @Test
    void equalsAndComparisons() {
        EvidenceContext ctx = context(Map.of(
                "deployment.replicas", 1,
                "flag", true,
                "name", "keycloak"));

        assertThat(ConditionEvaluator.matches(Map.of("key", "deployment.replicas", "lessThan", 2), ctx)).isTrue();
        assertThat(ConditionEvaluator.matches(Map.of("key", "deployment.replicas", "greaterThanOrEqual", 2), ctx))
                .isFalse();
        assertThat(ConditionEvaluator.matches(Map.of("key", "flag", "equals", true), ctx)).isTrue();
        assertThat(ConditionEvaluator.matches(Map.of("key", "name", "equals", "KEYCLOAK"), ctx)).isTrue();
        assertThat(ConditionEvaluator.matches(Map.of("key", "name", "notEquals", "other"), ctx)).isTrue();
    }

    @Test
    void existsEmptyAndSize() {
        EvidenceContext ctx = context(Map.of(
                "list", List.of("a", "b"),
                "emptyStr", "  "));

        assertThat(ConditionEvaluator.matches(Map.of("key", "list", "exists", true), ctx)).isTrue();
        assertThat(ConditionEvaluator.matches(Map.of("key", "missing", "notExists", true), ctx)).isTrue();
        assertThat(ConditionEvaluator.matches(Map.of("key", "emptyStr", "empty", true), ctx)).isTrue();
        assertThat(ConditionEvaluator.matches(Map.of("key", "list", "sizeGreaterThan", 1), ctx)).isTrue();
        assertThat(ConditionEvaluator.matches(Map.of("key", "list", "contains", "a"), ctx)).isTrue();
    }

    @Test
    void allAndAny() {
        EvidenceContext ctx = context(Map.of("a", 1, "b", 2));

        assertThat(ConditionEvaluator.matches(
                        Map.of(
                                "all",
                                List.of(
                                        Map.of("key", "a", "equals", 1),
                                        Map.of("key", "b", "greaterThan", 1))),
                        ctx))
                .isTrue();

        assertThat(ConditionEvaluator.matches(
                        Map.of(
                                "any",
                                List.of(
                                        Map.of("key", "a", "equals", 99),
                                        Map.of("key", "b", "equals", 2))),
                        ctx))
                .isTrue();

        assertThat(ConditionEvaluator.matches(
                        Map.of(
                                "all",
                                List.of(
                                        Map.of("key", "a", "equals", 1),
                                        Map.of("key", "b", "equals", 99))),
                        ctx))
                .isFalse();
    }

    @Test
    void missingEvidenceThrows() {
        EvidenceContext ctx = context(Map.of());
        assertThatThrownBy(() -> ConditionEvaluator.matches(Map.of("key", "x", "equals", 1), ctx))
                .isInstanceOf(ConditionEvaluator.MissingEvidenceException.class);
    }

    @Test
    void validateRejectsUnknownOperator() {
        assertThatThrownBy(() -> ConditionEvaluator.validate(Map.of("key", "x", "regex", ".*")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown condition operator");
    }

    private static EvidenceContext context(Map<String, Object> values) {
        Instant now = Instant.now();
        List<Evidence> evidence = values.entrySet().stream()
                .map(e -> new Evidence("t1", "test", "cat", e.getKey(), e.getValue(), now))
                .toList();
        return new EvidenceContext("t1", evidence);
    }
}
