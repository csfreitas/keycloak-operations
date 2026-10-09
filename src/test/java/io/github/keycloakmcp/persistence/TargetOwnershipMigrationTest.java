package io.github.keycloakmcp.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** Populated V10 migration in an owned schema of the existing disposable test database. */
@QuarkusTest
class TargetOwnershipMigrationTest {
    private static final List<String> TABLES = List.of("targets", "target_tags", "assessment_runs",
            "assessment_findings", "health_check_runs", "health_check_results", "audit_events",
            "environment_snapshots", "inventory_snapshots", "installation_discovery_runs", "change_records");

    @Inject DataSource dataSource;
    private String schema;
    private boolean schemaCreated;

    @BeforeEach
    void populatedVersionTenSchema() throws SQLException {
        schema = "onb1_migration_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE SCHEMA " + ownedSchema());
            schemaCreated = true;
        }
        Flyway versionTen = migration("10");
        versionTen.migrate();
        assertThat(versionTen.info().current().getVersion().toString()).isEqualTo("10");
        populateHistoricalRows();
    }

    @AfterEach
    void removeOnlyTheSchemaThisTestCreated() throws SQLException {
        if (!schemaCreated) return;
        // The identifier is generated here and validated again; public/shared schemas are never targets.
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DROP SCHEMA " + ownedSchema() + " CASCADE");
            schemaCreated = false;
        }
    }

    @Test
    void migrationPreservesAllExistingValuesAndDoesNotInferConfigurationOwnership() throws SQLException {
        Map<String, List<String>> before = historicalSnapshot();

        Flyway versionEleven = migration("11");
        versionEleven.migrate();

        assertThat(versionEleven.info().current().getVersion().toString()).isEqualTo("11");
        assertThat(historicalSnapshot()).isEqualTo(before);
        // One row deliberately has the same ID as a configured application target.
        assertThat(rows("SELECT id, registry_owner, registry_revision FROM " + table("targets") + " ORDER BY id"))
                .containsExactly(List.of("lab-keycloak-a", "LEGACY_UNCLASSIFIED", "0"),
                        List.of("legacy-target", "LEGACY_UNCLASSIFIED", "0"));
        assertThat(rows("SELECT installation_uid, installation_revision, installation_managed::text FROM "
                + table("targets") + " WHERE id = 'legacy-target'"))
                .containsExactly(List.of("legacy-root-uid", "7", "true"));
    }

    @Test
    void constraintsRejectUnknownOwnersNullsAndNegativeRevisionsWithoutChangingRows() throws SQLException {
        migration("11").migrate();
        String targets = table("targets");

        assertSqlState("UPDATE " + targets + " SET registry_owner = 'MANAGED' WHERE id = 'legacy-target'", "23514");
        assertSqlState("UPDATE " + targets + " SET registry_revision = -1 WHERE id = 'legacy-target'", "23514");
        assertSqlState("UPDATE " + targets + " SET registry_owner = NULL WHERE id = 'legacy-target'", "23502");
        assertSqlState("UPDATE " + targets + " SET registry_revision = NULL WHERE id = 'legacy-target'", "23502");

        assertThat(rows("SELECT registry_owner, registry_revision FROM " + targets + " WHERE id = 'legacy-target'"))
                .containsExactly(List.of("LEGACY_UNCLASSIFIED", "0"));
    }

    @Test
    void nativeInsertWithoutOwnershipDoesNotAcquireConfigurationAuthority() throws SQLException {
        migration("11").migrate();
        execute("""
                INSERT INTO %s (id, display_name, product_type, environment, keycloak_url,
                  keycloak_auth_realm, keycloak_client_id, keycloak_credential_ref)
                VALUES ('new-unclassified', 'New fixture', 'KEYCLOAK', 'TEST',
                  'https://keycloak.example.invalid', 'master', 'reader', 'credential-reference')
                """.formatted(table("targets")));

        assertThat(rows("SELECT registry_owner, registry_revision FROM " + table("targets")
                + " WHERE id = 'new-unclassified'"))
                .containsExactly(List.of("LEGACY_UNCLASSIFIED", "0"));
    }

    private Flyway migration(String version) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .schemas(schema).defaultSchema(schema).createSchemas(false).cleanDisabled(true)
                .target(version).load();
    }

    private void populateHistoricalRows() throws SQLException {
        execute("""
                INSERT INTO %s (id, display_name, product_type, environment, enabled, keycloak_url,
                  keycloak_auth_realm, keycloak_client_id, keycloak_credential_ref, infra_type,
                  infra_cluster_id, infra_namespace, infra_credential_ref, observability,
                  installation_api_version, installation_kind, installation_name, installation_uid,
                  installation_revision, installation_managed, created_at, updated_at)
                VALUES ('legacy-target', 'Historical target', 'RHBK', 'TEST', true,
                  'https://keycloak.example.invalid/auth', 'master', 'reader', 'source-reference', 'OPENSHIFT',
                  'cluster-identity', 'iam', 'cluster-reference',
                  '{"metricsType":"PROMETHEUS","credentialRef":"metrics-reference"}'::jsonb,
                  'apps/v1', 'Deployment', 'rhbk', 'legacy-root-uid', 7, true,
                  '2026-09-01T10:00:00Z', '2026-09-02T10:00:00Z'),
                ('lab-keycloak-a', 'Same ID as configured target', 'KEYCLOAK', 'DEV', false,
                  'https://different.example.invalid', 'master', 'other-reader', 'other-reference',
                  null, null, null, null, null, null, null, null, null, 0, false,
                  '2026-09-01T10:00:00Z', '2026-09-02T10:00:00Z')
                """.formatted(table("targets")));
        execute("INSERT INTO " + table("target_tags")
                + " (target_id, tag_key, tag_value) VALUES ('legacy-target', 'owner-team', 'synthetic-team'),"
                + " ('lab-keycloak-a', 'fixture', 'existing-record')");
        execute("""
                INSERT INTO %s (id, target_id, actor, context_hash, binding_revision, expires_at, consumed, candidates)
                VALUES ('historical-discovery', 'legacy-target', 'synthetic-issuer#operator', repeat('a', 64),
                  7, '2099-01-01T00:00:00Z', false,
                  '{"candidate":{"apiVersion":"apps/v1","kind":"Deployment","name":"rhbk","uid":"legacy-root-uid"}}'::jsonb)
                """.formatted(table("installation_discovery_runs")));
        execute("""
                INSERT INTO %s (id, target_id, profile, score, status, trigger_type, summary, started_at,
                  completed_at, created_at, evidence_completeness, confidence, category_scores,
                  rules_evaluated, rules_matched, rules_skipped, rules_not_evaluated)
                VALUES ('historical-assessment', 'legacy-target', 'fixture-profile', 64, 'PARTIAL', 'API',
                  '{"historical":true}'::jsonb, '2026-09-03T10:00:00Z', '2026-09-03T10:01:00Z',
                  '2026-09-03T10:01:00Z', 75, 'MEDIUM', '{"security":64}'::jsonb, 4, 2, 1, 1)
                """.formatted(table("assessment_runs")));
        execute("""
                INSERT INTO %s (id, assessment_id, target_id, finding_key, title, severity,
                  engine_status, lifecycle_status, evidence, created_at, resource_type, resource_id)
                VALUES ('historical-finding', 'historical-assessment', 'legacy-target', 'fixture-rule',
                  'Historical finding', 'MEDIUM', 'FAIL', 'OPEN', '{"observed":false}'::jsonb,
                  '2026-09-03T10:01:00Z', 'REALM', 'synthetic-realm')
                """.formatted(table("assessment_findings")));
        execute("""
                INSERT INTO %s (id, target_id, overall_status, trigger_type, summary, started_at, created_at)
                VALUES ('historical-health', 'legacy-target', 'UNKNOWN', 'API', '{"missing":true}'::jsonb,
                  '2026-09-03T10:00:00Z', '2026-09-03T10:00:00Z')
                """.formatted(table("health_check_runs")));
        execute("""
                INSERT INTO %s (id, health_check_id, target_id, check_name, status, details, created_at, duration_ms)
                VALUES ('historical-health-result', 'historical-health', 'legacy-target', 'fixture-check',
                  'UNKNOWN', '{"reason":"NOT_CONFIGURED"}'::jsonb, '2026-09-03T10:00:00Z', 12)
                """.formatted(table("health_check_results")));
        execute("""
                INSERT INTO %s (id, source, target_id, operation, status, params, metadata, created_at)
                VALUES ('historical-audit', 'REST', 'legacy-target', 'INSTALLATION_BOUND', 'SUCCESS',
                  '{}'::jsonb, '{"revision":7,"selectedUid":"legacy-root-uid"}'::jsonb, '2026-09-03T10:00:00Z')
                """.formatted(table("audit_events")));
        execute("""
                INSERT INTO %s (id, target_id, snapshot_hash, summary, created_at)
                VALUES ('historical-environment', 'legacy-target', repeat('b', 64),
                  '{"runtime":"OPENSHIFT"}'::jsonb, '2026-09-03T10:00:00Z')
                """.formatted(table("environment_snapshots")));
        execute("""
                INSERT INTO %s (id, target_id, environment_snapshot_id, inventory_type, summary, created_at)
                VALUES ('historical-inventory', 'legacy-target', 'historical-environment', 'INFRASTRUCTURE',
                  '{"installationUid":"legacy-root-uid"}'::jsonb, '2026-09-03T10:00:00Z')
                """.formatted(table("inventory_snapshots")));
        execute("""
                INSERT INTO %s (id, target_id, environment, resource_type, resource_id, realm, operation,
                  status, desired_state, actor, policy_revision, target_context_fingerprint, integrity_fingerprint,
                  created_at, updated_at)
                VALUES ('historical-change', 'legacy-target', 'TEST', 'CLIENT', 'synthetic-client',
                  'synthetic-realm', 'UPDATE', 'DRAFT', '{"enabled":false}'::jsonb,
                  'synthetic-issuer#operator', 'historical-policy', repeat('c', 64), repeat('d', 64),
                  '2026-09-03T10:00:00Z', '2026-09-03T10:00:00Z')
                """.formatted(table("change_records")));
    }

    private Map<String, List<String>> historicalSnapshot() throws SQLException {
        Map<String, List<String>> snapshot = new LinkedHashMap<>();
        for (String name : TABLES) {
            String order = "target_tags".equals(name) ? "target_id, tag_key" : "id";
            String projection = "targets".equals(name)
                    ? "(to_jsonb(t) - 'registry_owner' - 'registry_revision')::text" : "to_jsonb(t)::text";
            snapshot.put(name, rows("SELECT " + projection + " FROM " + table(name) + " t ORDER BY " + order)
                    .stream().map(row -> row.getFirst()).toList());
        }
        return Map.copyOf(snapshot);
    }

    private void assertSqlState(String sql, String state) {
        assertThatThrownBy(() -> execute(sql)).isInstanceOfSatisfying(SQLException.class,
                exception -> assertThat(exception.getSQLState()).isEqualTo(state));
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private List<List<String>> rows(String sql) throws SQLException {
        List<List<String>> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            int columns = result.getMetaData().getColumnCount();
            while (result.next()) {
                List<String> values = new ArrayList<>();
                for (int index = 1; index <= columns; index++) values.add(result.getString(index));
                rows.add(values);
            }
        }
        return rows;
    }

    private String table(String name) {
        if (!TABLES.contains(name)) throw new IllegalArgumentException("Unowned fixture table");
        return ownedSchema() + ".\"" + name + "\"";
    }

    private String ownedSchema() {
        if (schema == null || !schema.matches("onb1_migration_[a-f0-9]{32}")) {
            throw new IllegalStateException("Invalid owned migration fixture schema");
        }
        return "\"" + schema + "\"";
    }
}
