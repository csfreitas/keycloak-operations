package io.github.keycloakmcp.testsupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
    private static final String DATABASE_NAME = "kcops_test";
    private static final String DATABASE_USER = "kcops_test";
    private static final String DATABASE_PASSWORD = "kcops_test";
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(60);

    private PostgreSQLContainer<?> postgres;
    private String podmanContainerName;

    @Override
    public Map<String, String> start() {
        String runId = UUID.randomUUID().toString().substring(0, 8);
        if (usePodmanCli()) {
            return startWithPodman(runId);
        }

        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                .withDatabaseName(DATABASE_NAME)
                .withUsername(DATABASE_USER)
                .withPassword(DATABASE_PASSWORD)
                .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
                .withLabel(PROJECT_LABEL, PROJECT_LABEL_VALUE)
                .withLabel("io.github.keycloak-operations.purpose", "automated-tests")
                .withCreateContainerCmdModifier(command -> command.withName(CONTAINER_NAME_PREFIX + runId))
                .withReuse(false);
        postgres.start();

        return datasourceConfig(postgres.getJdbcUrl());
    }

    @Override
    public void stop() {
        if (postgres != null) {
            postgres.stop();
            postgres = null;
        }
        if (podmanContainerName != null) {
            if (commandSucceeds(Duration.ofSeconds(5), "podman", "container", "exists", podmanContainerName)) {
                runCommand(COMMAND_TIMEOUT, "podman", "rm", "--force", podmanContainerName);
            }
            podmanContainerName = null;
        }
    }

    private Map<String, String> startWithPodman(String runId) {
        podmanContainerName = CONTAINER_NAME_PREFIX + runId;
        try {
            runCommand(COMMAND_TIMEOUT,
                    "podman", "run", "--detach", "--rm",
                    "--name", podmanContainerName,
                    "--label", PROJECT_LABEL + "=" + PROJECT_LABEL_VALUE,
                    "--label", "io.github.keycloak-operations.purpose=automated-tests",
                    "--tmpfs", "/var/lib/postgresql/data:rw",
                    "--env", "POSTGRES_DB=" + DATABASE_NAME,
                    "--env", "POSTGRES_USER=" + DATABASE_USER,
                    "--env", "POSTGRES_PASSWORD=" + DATABASE_PASSWORD,
                    "--publish", "127.0.0.1::5432",
                    "postgres:16");

            String portOutput = runCommand(COMMAND_TIMEOUT,
                    "podman", "port", podmanContainerName, "5432/tcp");
            String binding = portOutput.lines().findFirst()
                    .orElseThrow(() -> new IllegalStateException("Podman did not report the PostgreSQL port"));
            int separator = binding.lastIndexOf(':');
            if (separator < 0 || separator == binding.length() - 1) {
                throw new IllegalStateException("Unexpected Podman port binding: " + binding);
            }

            String jdbcUrl = "jdbc:postgresql://localhost:"
                    + binding.substring(separator + 1) + "/" + DATABASE_NAME;
            awaitPodmanPostgres();
            return datasourceConfig(jdbcUrl);
        } catch (RuntimeException exception) {
            stop();
            throw exception;
        }
    }

    private static Map<String, String> datasourceConfig(String jdbcUrl) {
        return Map.of(
                "quarkus.datasource.jdbc.url", jdbcUrl,
                "quarkus.datasource.username", DATABASE_USER,
                "quarkus.datasource.password", DATABASE_PASSWORD,
                "quarkus.datasource.devservices.enabled", "false");
    }

    private void awaitPodmanPostgres() {
        Instant deadline = Instant.now().plus(STARTUP_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (commandSucceeds(Duration.ofSeconds(5),
                    "podman", "exec", podmanContainerName,
                    "pg_isready", "-U", DATABASE_USER, "-d", DATABASE_NAME)) {
                return;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for PostgreSQL", interrupted);
            }
        }
        throw new IllegalStateException("PostgreSQL did not become ready");
    }

    private static boolean usePodmanCli() {
        boolean macOs = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
        if (!macOs) {
            return false;
        }
        return commandSucceeds(Duration.ofSeconds(5),
                "podman", "version", "--format", "{{.Client.Version}}");
    }

    private static boolean commandSucceeds(Duration timeout, String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String runCommand(Duration timeout, String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Timed out running container command: " + command[0]);
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Container command failed: " + command[0] + ": " + output);
            }
            return output;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not run container command: " + command[0], exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running container command: " + command[0], exception);
        }
    }
}
