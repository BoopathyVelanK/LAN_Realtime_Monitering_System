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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end proof that a detected {@link DetectionResult} survives the
 * full production path - {@link AlertService}, the real Spring Data JPA
 * repositories, and Flyway's actual V1-V8 migrations - against a real
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
 * Test data is cleaned up via each test method's own transaction rollback
 * (see {@code @Transactional} below - Spring gives each @Test its own
 * transaction/rollback under a class-level @Transactional) in addition to
 * the container itself being destroyed after the test class - nothing
 * here persists beyond this run.
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

    @PersistenceContext
    private EntityManager entityManager;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void detectedResult_flowsThroughAlertServiceAndIsPersistedToPostgres() {
        DetectionRule rule = persistRule();
        User user = persistUser();
        EndpointDevice endpoint = persistEndpoint();
        commitFixtures();

        DetectionResult detectionResult = detectionResultFor(rule, user, endpoint);

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

    // =====================================================================
    // Alert deduplication
    // =====================================================================

    @Test
    void sequentialDuplicateDetections_resultInExactlyOneOpenAlert() {
        DetectionRule rule = persistRule();
        User user = persistUser();
        commitFixtures();
        DetectionResult detectionResult = detectionResultFor(rule, user, null);

        Optional<Alert> first = alertService.createAlertFrom(detectionResult);
        alertRepository.flush();
        Optional<Alert> second = alertService.createAlertFrom(detectionResult);
        alertRepository.flush();

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertEquals(first.get().getId(), second.get().getId(),
            "A duplicate OPEN detection for the same (user, rule) must reuse the existing alert");

        List<Alert> alertsForUserAndRule = allAlertsFor(user.getId(), rule.getId());
        assertEquals(1, alertsForUserAndRule.size(),
            "Exactly one alert row should exist for this (user, rule) pair");
        assertEquals(Alert.Status.OPEN, alertsForUserAndRule.get(0).getStatus());
    }

    @Test
    void acknowledgedAlert_allowsANewOpenAlertForSameUserAndRule() {
        DetectionRule rule = persistRule();
        User user = persistUser();
        commitFixtures();
        DetectionResult detectionResult = detectionResultFor(rule, user, null);

        Alert first = alertService.createAlertFrom(detectionResult).orElseThrow();
        alertRepository.flush();

        first.setStatus(Alert.Status.ACKNOWLEDGED);
        alertRepository.saveAndFlush(first);
        // Same REQUIRES_NEW visibility gap as above: the status change just
        // flushed here is only visible in THIS transaction until it's
        // actually committed. Without committing it now, the next
        // AlertInsertExecutor.insertAlert() call below would run in a
        // separate transaction that still sees `first` as OPEN, and its
        // own OPEN insert for the same (user_id, rule_id) would collide
        // with idx_alerts_open_user_rule_dedup instead of succeeding.
        commitFixtures();

        Alert second = alertService.createAlertFrom(detectionResult).orElseThrow();
        alertRepository.flush();

        assertNotEquals(first.getId(), second.getId(),
            "An ACKNOWLEDGED alert must not suppress a new OPEN alert for the same (user, rule)");
        assertEquals(Alert.Status.OPEN, second.getStatus());

        List<Alert> alertsForUserAndRule = allAlertsFor(user.getId(), rule.getId());
        assertEquals(2, alertsForUserAndRule.size());
    }

    @Test
    void resolvedAlert_allowsANewOpenAlertForSameUserAndRule() {
        DetectionRule rule = persistRule();
        User user = persistUser();
        commitFixtures();
        DetectionResult detectionResult = detectionResultFor(rule, user, null);

        Alert first = alertService.createAlertFrom(detectionResult).orElseThrow();
        alertRepository.flush();

        first.setStatus(Alert.Status.RESOLVED);
        alertRepository.saveAndFlush(first);
        // See acknowledgedAlert_allowsANewOpenAlertForSameUserAndRule for
        // why this status transition must be committed, not just flushed,
        // before the next REQUIRES_NEW insert attempt below.
        commitFixtures();

        Alert second = alertService.createAlertFrom(detectionResult).orElseThrow();
        alertRepository.flush();

        assertNotEquals(first.getId(), second.getId(),
            "A RESOLVED alert must not suppress a new OPEN alert for the same (user, rule)");
        assertEquals(Alert.Status.OPEN, second.getStatus());

        List<Alert> alertsForUserAndRule = allAlertsFor(user.getId(), rule.getId());
        assertEquals(2, alertsForUserAndRule.size());
    }

    @Test
    void differentUserOrRule_allowsSeparateAlerts() {
        DetectionRule ruleA = persistRule();
        DetectionRule ruleB = persistRule();
        User userA = persistUser();
        User userB = persistUser();
        commitFixtures();

        Alert sameRuleDifferentUser1 = alertService.createAlertFrom(
            detectionResultFor(ruleA, userA, null)).orElseThrow();
        Alert sameRuleDifferentUser2 = alertService.createAlertFrom(
            detectionResultFor(ruleA, userB, null)).orElseThrow();
        Alert sameUserDifferentRule = alertService.createAlertFrom(
            detectionResultFor(ruleB, userA, null)).orElseThrow();
        alertRepository.flush();

        assertNotEquals(sameRuleDifferentUser1.getId(), sameRuleDifferentUser2.getId(),
            "Different users must not be deduplicated against each other, even for the same rule");
        assertNotEquals(sameRuleDifferentUser1.getId(), sameUserDifferentRule.getId(),
            "Different rules must not be deduplicated against each other, even for the same user");

        assertEquals(1, allAlertsFor(userA.getId(), ruleA.getId()).size());
        assertEquals(1, allAlertsFor(userB.getId(), ruleA.getId()).size());
        assertEquals(1, allAlertsFor(userA.getId(), ruleB.getId()).size());
    }

    @Test
    void postgresUniqueIndex_rejectsADuplicateOpenRowAtDatabaseLevel() {
        DetectionRule rule = persistRule();
        User user = persistUser();

        Alert first = rawAlert(rule, user, Alert.Status.OPEN);
        alertRepository.saveAndFlush(first);

        Alert duplicate = rawAlert(rule, user, Alert.Status.OPEN);

        assertThrows(DataIntegrityViolationException.class, () -> alertRepository.saveAndFlush(duplicate),
            "The partial unique index from V8 must reject a second OPEN alert "
                + "for the same (user_id, rule_id) inserted directly, bypassing AlertService's dedup check");
    }

    @Test
    void v8Migration_appliesSuccessfully_andCreatesTheDedupIndex() {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery(
                "SELECT 1 FROM pg_indexes WHERE tablename = 'alerts' AND indexname = :indexName")
            .setParameter("indexName", "idx_alerts_open_user_rule_dedup")
            .getResultList();

        assertFalse(rows.isEmpty(),
            "V8__add_alert_open_dedup_index.sql must have created idx_alerts_open_user_rule_dedup on alerts");
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /**
     * Commits everything persisted so far in the current test transaction
     * and immediately opens a fresh one, so that a later
     * {@code Propagation.REQUIRES_NEW} call (AlertInsertExecutor.insertAlert)
     * - which runs on a genuinely separate physical transaction/connection -
     * can actually see this data.
     *
     * Why this is needed: this test class rolls back its transaction at the
     * end of each {@code @Test} method (standard {@code @Transactional}
     * test cleanup), so a row created via persistRule()/persistUser()/
     * persistEndpoint(), or a status change flushed via
     * alertRepository.saveAndFlush(...), is only ever flushed - never
     * committed - for as long as that transaction stays open. Under
     * PostgreSQL's default READ COMMITTED isolation, AlertInsertExecutor's
     * own REQUIRES_NEW transaction cannot see anything this transaction
     * hasn't committed yet, so a foreign key check (alerts_rule_id_fkey,
     * alerts_user_id_fkey) or the idx_alerts_open_user_rule_dedup partial
     * unique index would evaluate against stale/missing data - not because
     * the row doesn't exist, but because it isn't visible yet to that
     * other transaction.
     *
     * Any row committed here is not covered by this test method's own
     * final rollback and persists for the lifetime of the shared
     * Testcontainers instance (torn down once the whole test class
     * finishes). That's acceptable: every fixture here uses a fresh random
     * UUID/name per test (see persistRule/persistUser/persistEndpoint), so
     * there's no cross-test collision, and no assertion in this class
     * depends on total row counts - only on rows scoped to a specific
     * user/rule id.
     */
    private void commitFixtures() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
    }

    private DetectionResult detectionResultFor(DetectionRule rule, User user, EndpointDevice endpoint) {
        return new DetectionResult(
            true,
            rule.getId(),
            DetectionRule.Severity.HIGH,
            "Repeated failed login attempts detected",
            "5 failed login attempts observed for user within the last 300 seconds (threshold: 5).",
            user == null ? null : user.getId(),
            endpoint == null ? null : endpoint.getId()
        );
    }

    /** Raw Alert construction that bypasses AlertService entirely - used
     * only to prove the database-level constraint fires independent of
     * the application-level dedup check. */
    private Alert rawAlert(DetectionRule rule, User user, Alert.Status status) {
        Alert alert = new Alert();
        alert.setRule(rule);
        alert.setUser(user);
        alert.setSeverity(Alert.Severity.HIGH);
        alert.setTitle("Raw duplicate-index probe alert");
        alert.setStatus(status);
        return alert;
    }

    private List<Alert> allAlertsFor(UUID userId, UUID ruleId) {
        return alertRepository.findAll().stream()
            .filter(a -> a.getUser() != null && userId.equals(a.getUser().getId()))
            .filter(a -> a.getRule() != null && ruleId.equals(a.getRule().getId()))
            .toList();
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
