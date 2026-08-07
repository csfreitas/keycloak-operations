package io.github.keycloakmcp.target;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class TargetSanitizationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void summaryAndDetailsNeverExposeSecretsOrCredentialRefOrUrl() throws Exception {
        Target target = new Target(
                TargetId.of("customer-a-prd"),
                "Customer A Production",
                TargetType.RHBK,
                TargetEnvironment.PRD,
                true,
                new KeycloakTargetConfiguration(
                        "https://sso.customer-a.example",
                        "master",
                        "keycloak-mcp",
                        "keycloak/customer-a-prd"),
                new InfrastructureTargetConfiguration(
                        InfrastructureType.OPENSHIFT,
                        "rosa-customer-a-prd",
                        "rhbk",
                        "openshift/customer-a-prd"),
                null,
                Map.of("customer", "customer-a"));

        TargetSummary summary = TargetMapper.toSummary(target);
        TargetDetails details = TargetMapper.toDetails(target);

        String summaryJson = mapper.writeValueAsString(summary);
        String detailsJson = mapper.writeValueAsString(details);
        String combined = summaryJson + detailsJson;

        assertThat(combined).doesNotContain("credential");
        assertThat(combined).doesNotContain("clientSecret");
        assertThat(combined).doesNotContain("password");
        assertThat(combined).doesNotContain("https://sso.customer-a.example");
        assertThat(combined).doesNotContain("keycloak/customer-a-prd");
        assertThat(combined).doesNotContain("openshift/customer-a-prd");

        assertThat(summary.id()).isEqualTo("customer-a-prd");
        assertThat(details.infrastructureConfigured()).isTrue();
        assertThat(details.infrastructureType()).isEqualTo("OPENSHIFT");
        assertThat(details.keycloakConfigured()).isTrue();

        List<TargetSummary> list = TargetMapper.toSummaries(List.of(target));
        assertThat(list).hasSize(1);
    }
}
