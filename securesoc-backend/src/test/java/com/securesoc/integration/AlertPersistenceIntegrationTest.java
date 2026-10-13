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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 * repositories, and Flyway's actual V1-V15 migrations - against a real
 * PostgreSQL instance, not H2 and not a developer's local database.
 *
 * The PostgreSQL instance is the suite-wide ephemeral Testcontainers
 * container ({@link SharedPostgresContainer}); it never shares state with
 * docker-compose.yml's "securesoc-postgres" container or any other
 * database. No database name, host, or port is hardcoded or must be
 * created by hand - Testcontainers assigns a random port on an
 * auto-created database inside the container, and {@link DynamicPropertySource}
 * wires that address into the Spring context before it starts.
 *
 * Test data is cleaned up in two layers: uncommitted work is undone by each
 * test method's own transaction rollback (class-level @Transactional), and
 * every row committed via {@code commitFixtures()} is tracked by id and
 * deleted in {@code cleanUpCommittedFixtures()}. The container is shared
 * with other test classes, so nothing may be left behind - in particular
 * the enabled AUTH_FAILURE rules this class creates would otherwise be
 * evaluated by every later class's detection runs.
 */
@SpringBootTest
@Transactional
class AlertPersistenceIntegrationTest {

    // Same base image docker-compose.yml uses for local dev, so behavior
    // (extensions, defaults) matches production as closely as possible.
    static final PostgreSQLContainer<?> POSTGRES = SharedPostgresContainer.INSTANCE;

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

    private final Set<UUID> createdRuleIds = new LinkedHashSet<>();
    private final Set<UUID> createdUserIds = new LinkedHashSet<>();
    private final Set<UUID> createdEndpointIds = new LinkedHashSet<>();

    /**
     * Deletes every row this class committed (see {@code commitFixtures()}).
     * Alerts are removed first (they reference the rule/user/endpoint), then
     * the endpoints, users and rules themselves.
     *
     * Interaction with Spring's test-managed transaction: this class is
     * {@code @Transactional}, and {@code @AfterEach} runs while the current test
     * transaction is still open. That transaction can only hold work that was
     * never committed - committed fixtures went through {@code commitFixtures()},
     * which commits and immediately opens a fresh transaction. After a
     * deliberate constraint violation (the unique-index tests) PostgreSQL has
     * also aborted it, so it can't be reused for the deletes. It is therefore
     * rolled back first (exactly what Spring would do at the end of the test
     * anyway), and the deletes run in their own transaction that is committed
     * on success. Ids tracked for rows that were only ever flushed (and so were
     * rolled back) are harmless: {@code deleteById} ignores missing rows.
     * If a delete fails, that transaction is rolled back (not left open for the
     * next test) and the failure propagates.
     */
    @AfterEach
    void cleanUpCommittedFixtures() {
        if (TestTransaction.isActive()) {
            TestTransaction.flagForRollback();
            TestTransaction.end();
        }
        TestTransaction.start();
        try {
            // Bulk delete by foreign-key id: no alert is loaded, so no detached lazy
            // association is touched. Alerts go first (alerts.endpoint_id/rule_id are
            // ON DELETE SET NULL, so deleting those rows first would orphan them).
            FixtureCleanup.deleteAlertsReferencing(entityManager, createdEndpointIds, createdRuleIds, createdUserIds);

            createdEndpointIds.forEach(endpointDeviceRepository::deleteById);
            endpointDeviceRepository.flush();

            createdUserIds.forEach(userRepository::deleteById);
            userRepository.flush();

            createdRuleIds.forEach(detectionRuleRepository::deleteById);
            detectionRuleRepository.flush();

            TestTransaction.flagForCommit();
        } finally {
            // Commits when flagged above, otherwise rolls back (cleanup failed).
            TestTransaction.end();
            createdRuleIds.clear();
            createdUserIds.clear();
            createdEndpointIds.clear();
        }
    }

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
    void sequentialDuplicateEndpointDetections_reuseOneOpenAlert() {
        DetectionRule rule = persistRule();
        EndpointDevice endpoint = persistEndpoint();
        commitFixtures();

        DetectionResult result = detectionResultFor(rule, null, endpoint);

        Alert first = alertService.createAlertFrom(result).orElseThrow();
        alertRepository.flush();

        Alert second = alertService.createAlertFrom(result).orElseThrow();
        alertRepository.flush();

        assertEquals(
            first.getId(),
            second.getId(),
            "Repeated detections for the same endpoint and rule must reuse the OPEN alert"
        );

        assertEquals(Alert.Status.OPEN, second.getStatus());
        assertEquals(endpoint.getId(), second.getEndpoint().getId());
        assertEquals(rule.getId(), second.getRule().getId());
    }


@Test
void acknowledgedEndpointAlert_allowsNewOpenAlert() {
    DetectionRule rule = persistRule();
    EndpointDevice endpoint = persistEndpoint();
    commitFixtures();

    DetectionResult result = detectionResultFor(rule, null, endpoint);

    Alert first = alertService.createAlertFrom(result).orElseThrow();
    alertRepository.flush();

    first.setStatus(Alert.Status.ACKNOWLEDGED);
    alertRepository.saveAndFlush(first);
    commitFixtures();

    Alert second = alertService.createAlertFrom(result).orElseThrow();
    alertRepository.flush();

    assertNotEquals(first.getId(), second.getId());
    assertEquals(Alert.Status.ACKNOWLEDGED, first.getStatus());
    assertEquals(Alert.Status.OPEN, second.getStatus());
}

    @Test
    void resolvedEndpointAlert_allowsNewOpenAlert() {
        DetectionRule rule = persistRule();
        EndpointDevice endpoint = persistEndpoint();
        commitFixtures();

        DetectionResult result = detectionResultFor(rule, null, endpoint);

        Alert first = alertService.createAlertFrom(result).orElseThrow();
        alertRepository.flush();

        first.setStatus(Alert.Status.RESOLVED);
        alertRepository.saveAndFlush(first);
        commitFixtures();

        Alert second = alertService.createAlertFrom(result).orElseThrow();
        alertRepository.flush();

        assertNotEquals(first.getId(), second.getId());
        assertEquals(Alert.Status.RESOLVED, first.getStatus());
        assertEquals(Alert.Status.OPEN, second.getStatus());
    }


    @Test
    void endpointDeduplication_doesNotCrossEndpointsOrRules() {
        DetectionRule ruleA = persistRule();
        DetectionRule ruleB = persistRule();
        EndpointDevice endpointA = persistEndpoint();
        EndpointDevice endpointB = persistEndpoint();
        commitFixtures();

        Alert endpointA_ruleA = alertService.createAlertFrom(
            detectionResultFor(ruleA, null, endpointA)).orElseThrow();

        Alert endpointB_ruleA = alertService.createAlertFrom(
            detectionResultFor(ruleA, null, endpointB)).orElseThrow();

        Alert endpointA_ruleB = alertService.createAlertFrom(
            detectionResultFor(ruleB, null, endpointA)).orElseThrow();

        alertRepository.flush();

        assertNotEquals(endpointA_ruleA.getId(), endpointB_ruleA.getId(),
            "Different endpoints must have separate alerts for the same rule");

        assertNotEquals(endpointA_ruleA.getId(), endpointA_ruleB.getId(),
            "Different rules must have separate alerts for the same endpoint");

        assertEquals(1, alertRepository
            .findByEndpoint_IdAndRule_IdAndStatusAndUserIsNull(
                endpointA.getId(), ruleA.getId(), Alert.Status.OPEN).stream().count());
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

    @Test
    void postgresUniqueIndex_rejectsDuplicateOpenEndpointAlert() {
        DetectionRule rule = persistRule();
        EndpointDevice endpoint = persistEndpoint();

        Alert first = rawAlert(rule, null, Alert.Status.OPEN);
        first.setEndpoint(endpoint);
        alertRepository.saveAndFlush(first);

        Alert duplicate = rawAlert(rule, null, Alert.Status.OPEN);
        duplicate.setEndpoint(endpoint);

        assertThrows(
            DataIntegrityViolationException.class,
            () -> alertRepository.saveAndFlush(duplicate),
            "V15 must reject a second OPEN alert for the same endpoint and rule"
        );
    }

    @Test
    void v15Migration_appliesSuccessfullyAndCreatesEndpointDedupIndex() {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery(
                "SELECT 1 FROM pg_indexes " +
                "WHERE tablename = 'alerts' " +
                "AND indexname = :indexName"
            )
            .setParameter(
                "indexName",
                "idx_alerts_open_endpoint_rule_dedup"
            )
            .getResultList();

        assertFalse(
            rows.isEmpty(),
            "V15 must create idx_alerts_open_endpoint_rule_dedup"
        );
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
     * final rollback. Every fixture uses a fresh random UUID/name per test
     * (see persistRule/persistUser/persistEndpoint) and its id is tracked so
     * {@link #cleanUpCommittedFixtures()} can delete it - the database is
     * shared by the whole suite and is not destroyed after this class.
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
        DetectionRule saved = detectionRuleRepository.save(rule);
        createdRuleIds.add(saved.getId());
        return saved;
    }

    private User persistUser() {
        String suffix = UUID.randomUUID().toString();
        User user = new User();
        user.setUsername("it-user-" + suffix);
        user.setEmail("it-user-" + suffix + "@example.invalid");
        user.setPasswordHash("integration-test-placeholder-hash");
        user.setFullName("Integration Test User");
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private EndpointDevice persistEndpoint() {
        EndpointDevice endpoint = new EndpointDevice();
        endpoint.setHostname("IT-ENDPOINT-" + UUID.randomUUID());
        endpoint.setMacAddress(randomMacAddress());
        endpoint.setAgentTokenHash(randomHex(64));
        EndpointDevice saved = endpointDeviceRepository.save(endpoint);
        createdEndpointIds.add(saved.getId());
        return saved;
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
