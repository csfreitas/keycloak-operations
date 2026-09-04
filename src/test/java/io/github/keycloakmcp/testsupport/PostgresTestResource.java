package io.github.keycloakmcp.testsupport;

import java.util.Map;
import java.util.UUID;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Provides one disposable, clearly identified PostgreSQL container for Quarkus tests.
 */
public final class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    static final String CONTAINER_NAME_PREFIX = "keycloak-operations-test-postgres-";
    static final String PROJECT_LABEL = "io.github.keycloak-operations.test-resource";
    static final String PROJECT_LABEL_VALUE = "postgresql";

    private PostgreSQLContainer<?> postgres;

    @Override
    public Map<String, String> start() {
        String runId = UUID.randomUUID().toString().substring(0, 8);
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                .withDatabaseName("kcops_test")
                .withUsername("kcops_test")
                .withPassword("kcops_test")
                .withLabel(PROJECT_LABEL, PROJECT_LABEL_VALUE)
                .withLabel("io.github.keycloak-operations.purpose", "automated-tests")
                .withCreateContainerCmdModifier(command -> command.withName(CONTAINER_NAME_PREFIX + runId))
                .withReuse(false);
        postgres.start();

        return Map.of(
                "quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
                "quarkus.datasource.username", postgres.getUsername(),
                "quarkus.datasource.password", postgres.getPassword(),
                "quarkus.datasource.devservices.enabled", "false");
    }

    @Override
    public void stop() {
        if (postgres != null) {
            postgres.stop();
            postgres = null;
        }
    }
}
