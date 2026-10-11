package com.securesoc.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointAlertDeduplicationMigrationTest {

    @Test
    void v15MigrationSupersedesOlderDuplicatesAndPreservesHistory()
            throws Exception {

        try (PostgreSQLContainer<?> postgres =
                     new PostgreSQLContainer<>("postgres:16-alpine")) {

            postgres.start();

            Flyway.configure()
                    .dataSource(
                            postgres.getJdbcUrl(),
                            postgres.getUsername(),
                            postgres.getPassword())
                    .locations("classpath:db/migration")
                    .target("14")
                    .load()
                    .migrate();

            UUID endpointId = UUID.randomUUID();
            UUID ruleId = UUID.randomUUID();

            UUID olderId = UUID.randomUUID();
            UUID newestId = UUID.randomUUID();

            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword())) {

                connection.setAutoCommit(false);

                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("""
                        INSERT INTO endpoint_devices
                            (id, hostname, mac_address, agent_token_hash)
                        VALUES
                            ('%s', 'migration-test-endpoint',
                             'AA:BB:CC:DD:EE:01', '%s')
                        """.formatted(endpointId, "a".repeat(64)));

                    statement.executeUpdate("""
                        INSERT INTO detection_rules
                            (id, name, rule_type, event_source, severity)
                        VALUES
                            ('%s', 'Migration test rule', 'THRESHOLD',
                             'NETWORK_USAGE', 'MEDIUM')
                        """.formatted(ruleId));
                }

                insertAlert(connection, olderId, endpointId, ruleId,
                        "Historical older alert",
                        "2026-10-01T10:00:00Z");

                insertAlert(connection, newestId, endpointId, ruleId,
                        "Historical newest alert",
                        "2026-10-02T10:00:00Z");

                connection.commit();
            }

            Flyway.configure()
                    .dataSource(
                            postgres.getJdbcUrl(),
                            postgres.getUsername(),
                            postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword())) {

                assertAlert(connection, olderId,
                        "SUPERSEDED", "Historical older alert");

                assertAlert(connection, newestId,
                        "OPEN", "Historical newest alert");

                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM alerts " +
                        "WHERE endpoint_id = ? AND rule_id = ?")) {

                    statement.setObject(1, endpointId);
                    statement.setObject(2, ruleId);

                    try (ResultSet result = statement.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals(2, result.getInt(1),
                                "Migration must preserve both historical rows");
                    }
                }

                assertIndexExists(connection,
                        "idx_alerts_open_endpoint_rule_dedup");

                assertIndexExists(connection,
                        "idx_alerts_open_user_rule_dedup");
            }
        }
    }

    private static void insertAlert(
            Connection connection,
            UUID alertId,
            UUID endpointId,
            UUID ruleId,
            String description,
            String createdAt) throws Exception {

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO alerts
                    (id, endpoint_id, rule_id, title, description,
                     severity, status, created_at, user_id)
                VALUES (?, ?, ?, ?, ?, 'MEDIUM', 'OPEN', ?::timestamptz, NULL)
                """)) {

            statement.setObject(1, alertId);
            statement.setObject(2, endpointId);
            statement.setObject(3, ruleId);
            statement.setString(4, "Migration test alert");
            statement.setString(5, description);
            statement.setString(6, createdAt);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void assertAlert(
            Connection connection,
            UUID id,
            String expectedStatus,
            String expectedDescription) throws Exception {

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT status, description FROM alerts WHERE id = ?")) {

            statement.setObject(1, id);

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "Historical alert must not be deleted");
                assertEquals(expectedStatus, result.getString("status"));
                assertEquals(expectedDescription,
                        result.getString("description"));
                assertNotNull(result.getString("description"));
            }
        }
    }

    private static void assertIndexExists(
            Connection connection,
            String indexName) throws Exception {

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM pg_indexes " +
                "WHERE tablename = 'alerts' AND indexname = ?")) {

            statement.setString(1, indexName);

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(),
                        "Expected migration index: " + indexName);
            }
        }
    }
}
