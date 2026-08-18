package com.securesoc.integration;

import com.securesoc.detection.DetectionResult;
import com.securesoc.entity.Alert;
import com.securesoc.entity.DetectionRule;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.User;
import com.securesoc.repository.AlertRepository;
import com.securesoc.repository.DetectionRuleRepository;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.repository.UserRepository;
import com.securesoc.service.AlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end proof that a detected {@link DetectionResult} survives the
 * full production path - {@link AlertService}, the real Spring Data JPA
 * repositories, and Flyway's actual V1-V7 migrations - against a real
 * PostgreSQL instance, not H2 and not a developer's local database.
 *
 * The PostgreSQL instance is an ephemeral Testcontainers container: a
 * fresh image is pulled/started for this test class only, torn down
 * automatically afterward, and never shares state with
 * docker-compose.yml's "securesoc-postgres" container or any other
 * database. No database name, host, or port is hardcoded or must be
 * created by hand - Testcontainers assigns a random port on an
 * auto-created database inside the container, and {@link DynamicPropertySource}
 * wires that address into the Spring context before it starts.
 *
 * Test data is cleaned up via the test's own transaction rollback (see
 * {@code @Transactional} below) in addition to the container itself being
 * destroyed after the test class - nothing here persists beyond this run.
 */
@Testcontainers
@SpringBootTest
@Transactional
class AlertPersistenceIntegrationTest {

    // Same base image docker-compose.yml uses for local dev, so behavior
    // (extensions, defaults) matches production as closely as possible.
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AlertService alertService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private DetectionRuleRepository detectionRuleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EndpointDeviceRepository endpointDeviceRepository;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void detectedResult_flowsThroughAlertServiceAndIsPersistedToPostgres() {
        DetectionRule rule = persistRule();
        User user = persistUser();
        EndpointDevice endpoint = persistEndpoint();

        DetectionResult detectionResult = new DetectionResult(
            true,
            rule.getId(),
            DetectionRule.Severity.HIGH,
            "Repeated failed login attempts detected",
            "5 failed login attempts observed for user within the last 300 seconds (threshold: 5).",
            user.getId(),
            endpoint.getId()
        );

        Optional<Alert> created = alertService.createAlertFrom(detectionResult);
        // Force the INSERT to actually execute against the container now,
        // rather than staying buffered in the persistence context - this
        // is what proves the write really reaches PostgreSQL (and would
        // surface a constraint violation immediately if the mapping were
        // wrong), not just that an in-memory object was built correctly.
        alertRepository.flush();

        assertTrue(created.isPresent(), "AlertService should persist an Alert for a detected DetectionResult");
        UUID alertId = created.get().getId();
        assertNotNull(alertId, "Persisted Alert must have a generated id");

        Optional<Alert> retrieved = alertRepository.findById(alertId);
        assertTrue(retrieved.isPresent(), "Persisted Alert must be retrievable via AlertRepository");

        Alert persisted = retrieved.get();
        assertNotNull(persisted.getRule(), "Persisted Alert must reference a DetectionRule");
        assertEquals(rule.getId(), persisted.getRule().getId());
        assertEquals(Alert.Severity.HIGH, persisted.getSeverity());
        assertEquals("Repeated failed login attempts detected", persisted.getTitle());
        assertEquals(
            "5 failed login attempts observed for user within the last 300 seconds (threshold: 5).",
            persisted.getDescription());
        assertNotNull(persisted.getUser(), "Persisted Alert must reference the supplied User");
        assertEquals(user.getId(), persisted.getUser().getId());
        assertNotNull(persisted.getEndpoint(), "Persisted Alert must reference the supplied EndpointDevice");
        assertEquals(endpoint.getId(), persisted.getEndpoint().getId());
    }

    private DetectionRule persistRule() {
        DetectionRule rule = new DetectionRule();
        rule.setName("IT Repeated Failed Login " + UUID.randomUUID());
        rule.setDescription("Integration-test-only rule; rolled back after the test.");
        rule.setRuleType(DetectionRule.RuleType.THRESHOLD);
        rule.setEventSource("AUTH_FAILURE");
        rule.setThreshold(5);
        rule.setWindowSeconds(300);
        rule.setSeverity(DetectionRule.Severity.HIGH);
        rule.setEnabled(true);
        return detectionRuleRepository.save(rule);
    }

    private User persistUser() {
        String suffix = UUID.randomUUID().toString();
        User user = new User();
        user.setUsername("it-user-" + suffix);
        user.setEmail("it-user-" + suffix + "@example.invalid");
        user.setPasswordHash("integration-test-placeholder-hash");
        user.setFullName("Integration Test User");
        return userRepository.save(user);
    }

    private EndpointDevice persistEndpoint() {
        EndpointDevice endpoint = new EndpointDevice();
        endpoint.setHostname("IT-ENDPOINT-" + UUID.randomUUID());
        endpoint.setMacAddress(randomMacAddress());
        endpoint.setAgentTokenHash(randomHex(64));
        return endpointDeviceRepository.save(endpoint);
    }

    /** endpoint_devices.mac_address is VARCHAR(17) UNIQUE NOT NULL, formatted
     * AA:BB:CC:DD:EE:FF - generate a random one so parallel/repeated test
     * runs never collide. */
    private static String randomMacAddress() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02X", bytes[i]));
            if (i < bytes.length - 1) {
                sb.append(":");
            }
        }
        return sb.toString();
    }

    /** endpoint_devices.agent_token_hash is VARCHAR(64) UNIQUE NOT NULL,
     * modeled on a SHA-256 hex digest - a random 64-char hex string is
     * sufficient here since no real agent token is ever involved. */
    private static String randomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        while (sb.length() < length) {
            sb.append(Integer.toHexString(RANDOM.nextInt(16)));
        }
        return sb.toString();
    }
}
