package io.github.keycloakmcp.observability.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.target.InfrastructureTargetConfiguration;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.ObservabilityTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetType;

class MetricsQueryContextIsolationTest {

    @Test
    void sameNamespaceStillIncludesTargetDiscriminator() {
        Target a = target("keycloak-a", "keycloak", Map.of("service", "keycloak-a"));
        Target b = target("keycloak-b", "keycloak", Map.of("service", "keycloak-b"));

        String selA = MetricsQueryContext.fromTarget(a).selectorClause();
        String selB = MetricsQueryContext.fromTarget(b).selectorClause();

        assertThat(selA).contains("namespace=\"keycloak\"");
        assertThat(selB).contains("namespace=\"keycloak\"");
        assertThat(selA).contains("service=\"keycloak-a\"");
        assertThat(selB).contains("service=\"keycloak-b\"");
        assertThat(selA).isNotEqualTo(selB);
    }

    @Test
    void namespaceAloneAddsTargetId() {
        Target t = target("lab-keycloak-a", "keycloak", Map.of());
        String sel = MetricsQueryContext.fromTarget(t).selectorClause();
        assertThat(sel).contains("namespace=\"keycloak\"");
        assertThat(sel).contains("target_id=\"lab-keycloak-a\"");
    }

    @Test
    void injectionPayloadCannotBreakSelectorStructure() {
        Target t = target(
                "lab-a",
                "ns",
                Map.of("service", "\"} or vector(1) or {"));
        String sel = MetricsQueryContext.fromTarget(t).selectorClause();
        assertThat(sel).contains("service=\"\\\"} or vector(1) or {\"");
        assertThat(sel).startsWith("namespace=");
        // Closing brace of selector is only after all labels — payload stays inside quotes
        assertThat(sel.chars().filter(c -> c == '"').count()).isGreaterThanOrEqualTo(6);
    }

    private static Target target(String id, String namespace, Map<String, String> tags) {
        return new Target(
                TargetId.of(id),
                id,
                TargetType.KEYCLOAK,
                TargetEnvironment.DEV,
                true,
                new KeycloakTargetConfiguration("http://localhost:8080", "master", "cli", "ref-" + id, null),
                new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, null, namespace, "infra-" + id),
                new ObservabilityTargetConfiguration(
                        "PROMETHEUS", null, "http://localhost:9090", null, namespace, "NAMESPACE"),
                tags);
    }
}
