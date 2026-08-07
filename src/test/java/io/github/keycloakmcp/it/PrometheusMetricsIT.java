package io.github.keycloakmcp.it;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Optional live Prometheus integration. Skipped unless {@code RUN_PROMETHEUS_IT=true}.
 */
@EnabledIfEnvironmentVariable(named = "RUN_PROMETHEUS_IT", matches = "true")
class PrometheusMetricsIT {

    @Test
    void prometheusReachableWhenEnabled() throws Exception {
        String endpoint = System.getenv().getOrDefault("PROMETHEUS_URL", "http://localhost:9090");
        Assumptions.assumeTrue(endpoint != null && !endpoint.isBlank());
        var client = java.net.http.HttpClient.newHttpClient();
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(endpoint.replaceAll("/$", "") + "/-/healthy"))
                .GET()
                .build();
        var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        Assumptions.assumeTrue(response.statusCode() == 200, "Prometheus not reachable");
        org.assertj.core.api.Assertions.assertThat(response.body()).containsIgnoringCase("Healthy");
    }
}
