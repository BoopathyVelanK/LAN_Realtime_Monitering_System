package com.securesoc.integration;

import com.securesoc.detection.DetectionContext;
import com.securesoc.detection.DetectionEngine;
import com.securesoc.detection.DetectionResult;
import com.securesoc.entity.Alert;
import com.securesoc.entity.AuthFailureEvent;
import com.securesoc.entity.DetectionRule;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.RiskScore;
import com.securesoc.entity.User;
import com.securesoc.repository.AlertRepository;
import com.securesoc.repository.AuthFailureEventRepository;
import com.securesoc.repository.DetectionRuleRepository;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.repository.RiskScoreRepository;
import com.securesoc.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end proof that Checkpoint B's {@link DetectionEngine} -&gt;
 * {@code RiskScoreService} wiring persists a real {@link RiskScore} row -
 * alongside the existing {@link Alert} persistence - against a real
 * PostgreSQL instance (Testcontainers), not H2 and not a developer's local
 * database. Follows the same container/fixture/commit conventions as
 * {@link AlertPersistenceIntegrationTest} (see that class's javadoc for the
 * full rationale); this class only covers what that one doesn't - the
 * RiskScoreService side effect of a detection, and its independence from
 * Alert deduplication - not a re-test of Alert dedup/ack/resolve behavior,
 * which stays covered by the unmodified {@link AlertPersistenceIntegrationTest}.
 *
 * Unlike {@code AlertPersistenceIntegrationTest} (which calls
 * {@code AlertService.createAlertFrom} directly with a hand-built
 * {@link DetectionResult}), these tests drive the real, Spring-wired
 * {@code RepeatedFailedLoginDetector} through {@link DetectionEngine#evaluate},
 * via real {@link AuthFailureEvent} rows - this is what actually exercises
 * the production wiring added in Checkpoint B, not just RiskScoreService in
 * isolation (already covered by RiskScoreServiceTest's unit tests).
 */
@Testcontainers
@SpringBootTest
@Transactional
class RiskScorePersistenceIntegrationTest {

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
    private DetectionEngine detectionEngine;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Autowired
    private DetectionRuleRepository detectionRuleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EndpointDeviceRepository endpointDeviceRepository;

    @Autowired
    private AuthFailureEventRepository authFailureEventRepository;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int THRESHOLD = 5;
    private static final int WINDOW_SECONDS = 300;

    // Populated by persistRule()/persistUser()/persistEndpoint() as each test
    // creates its fixtures, and drained by cleanUpCommittedFixtures() below.
    // Needed because commitFixtures() permanently commits these rows (see its
    // javadoc) with no rollback to rely on - without this, a DetectionRule
    // left behind by one test is still there for DetectionEngine.evaluate()
    // to match in every subsequent test, inflating the risk score it computes.
    private final Set<UUID> createdRuleIds = new LinkedHashSet<>();
    private final Set<UUID> createdUserIds = new LinkedHashSet<>();
    private final Set<UUID> createdEndpointIds = new LinkedHashSet<>();

    @Test
    void detectedResult_flowsThroughDetectionEngine_andPersistsBothAlertAndRiskScore() {
        DetectionRule rule = persistRule();
        User user = persistUser();
        EndpointDevice endpoint = persistEndpoint();
        persistFailureEvents(user, THRESHOLD);
        commitFixtures();

        Instant now = Instant.now();
        DetectionContext context = new DetectionContext("AUTH_FAILURE", endpoint.getId(), user.getId(), now, null);

        List<DetectionResult> results = detectionEngine.evaluate(context);
        alertRepository.flush();

        assertEquals(1, results.size(), "The rule's threshold is met, so one detection should occur");
        assertTrue(results.get(0).detected());

        // Existing Alert behavior still works, unmodified by Checkpoint B.
        List<Alert> alertsForUserAndRule = allAlertsFor(user.getId(), rule.getId());
        assertEquals(1, alertsForUserAndRule.size(), "AlertService should still persist exactly one Alert");
        assertEquals(Alert.Status.OPEN, alertsForUserAndRule.get(0).getStatus());

        // New in Checkpoint B: the same detection also produced a persisted RiskScore.
        Optional<RiskScore> riskScore = riskScoreRepository.findByEndpoint_Id(endpoint.getId());
        assertTrue(riskScore.isPresent(), "RiskScoreService should persist a RiskScore for the endpoint");
        assertEquals((short) 30, riskScore.get().getScore(), "HIGH severity should add 30 points from a score of 0");
        assertEquals(RiskScore.Level.LOW, riskScore.get().getLevel(), "A score of 30 falls in the LOW band (15-34)");
    }

    @Test
    void repeatedDetections_riskScoreAccumulatesAdditively_whileAlertStaysDeduplicatedToOne() {
        DetectionRule rule = persistRule();
        User user = persistUser();
        EndpointDevice endpoint = persistEndpoint();
        persistFailureEvents(user, THRESHOLD);
        commitFixtures();

        DetectionContext context = new DetectionContext("AUTH_FAILURE", endpoint.getId(), user.getId(), Instant.now(), null);

        detectionEngine.evaluate(context);
        alertRepository.flush();
        detectionEngine.evaluate(context);
        alertRepository.flush();

        // Alert deduplication is unaffected: still exactly one OPEN alert.
        List<Alert> alertsForUserAndRule = allAlertsFor(user.getId(), rule.getId());
        assertEquals(1, alertsForUserAndRule.size(),
            "Alert deduplication must still collapse two detections for the same (user, rule) into one OPEN alert");

        // Risk scoring is independent of that deduplication: both detections counted.
        RiskScore riskScore = riskScoreRepository.findByEndpoint_Id(endpoint.getId()).orElseThrow();
        assertEquals((short) 60, riskScore.getScore(),
            "Risk scoring must accumulate for every detected event regardless of Alert dedup (30 + 30)");
        assertEquals(RiskScore.Level.HIGH, riskScore.getLevel(), "A score of 60 falls in the HIGH band (60-79)");
    }

    @Test
    void nullEndpointDetection_stillCreatesAlert_butRecordsNoRiskScore() {
        DetectionRule rule = persistRule();
        User user = persistUser();
        persistFailureEvents(user, THRESHOLD);
        commitFixtures();

        long riskScoreCountBefore = riskScoreRepository.count();

        DetectionContext context = new DetectionContext("AUTH_FAILURE", null, user.getId(), Instant.now(), null);
        detectionEngine.evaluate(context);
        alertRepository.flush();

        // Existing Alert behavior for a null endpoint is unaffected.
        List<Alert> alertsForUserAndRule = allAlertsFor(user.getId(), rule.getId());
        assertEquals(1, alertsForUserAndRule.size(),
            "AlertService must still create an Alert when endpointId is null (e.g. a portal-only login)");

        // endpointId == null remains a no-op for risk scoring, end-to-end.
        assertEquals(riskScoreCountBefore, riskScoreRepository.count(),
            "No RiskScore row should be created or updated when the detection carries no endpointId");
    }

    // =====================================================================
    // Cleanup
    // =====================================================================

    /**
     * Deletes everything commitFixtures() permanently committed for the test
     * that just ran, plus whatever Alert/RiskScore rows the production code
     * under test (DetectionEngine.evaluate() -&gt; AlertInsertExecutor /
     * RiskScoreService, both REQUIRES_NEW) committed on top of it - none of
     * that is covered by this class's own transaction rollback, since it was
     * never part of that transaction to begin with.
     *
     * Deleted in FK-safe order: Alerts -&gt; RiskScore -&gt; AuthFailureEvents -&gt;
     * EndpointDevice -&gt; User -&gt; DetectionRule. Alerts and RiskScores aren't
     * separately tracked by id (they're created by the production code, not
     * these helpers) so they're located by the tracked user/rule/endpoint
     * ids they reference instead, mirroring allAlertsFor()'s own filtering.
     *
     * Runs in its own committed transaction so the deletes are real and not
     * undone by the standard @Transactional test rollback that follows.
     */
    @AfterEach
    void cleanUpCommittedFixtures() {
        if (!TestTransaction.isActive()) {
            TestTransaction.start();
        }

        alertRepository.findAll().stream()
            .filter(a -> (a.getUser() != null && createdUserIds.contains(a.getUser().getId()))
                || (a.getRule() != null && createdRuleIds.contains(a.getRule().getId()))
                || (a.getEndpoint() != null && createdEndpointIds.contains(a.getEndpoint().getId())))
            .forEach(alertRepository::delete);
        alertRepository.flush();

        createdEndpointIds.forEach(endpointId ->
            riskScoreRepository.findByEndpoint_Id(endpointId).ifPresent(riskScoreRepository::delete));
        riskScoreRepository.flush();

        authFailureEventRepository.findAll().stream()
            .filter(event -> event.getUser() != null && createdUserIds.contains(event.getUser().getId()))
            .forEach(authFailureEventRepository::delete);
        authFailureEventRepository.flush();

        createdEndpointIds.forEach(endpointDeviceRepository::deleteById);
        endpointDeviceRepository.flush();

        createdUserIds.forEach(userRepository::deleteById);
        userRepository.flush();

        createdRuleIds.forEach(detectionRuleRepository::deleteById);
        detectionRuleRepository.flush();

        TestTransaction.flagForCommit();
        TestTransaction.end();

        createdRuleIds.clear();
        createdUserIds.clear();
        createdEndpointIds.clear();
    }

    // =====================================================================
    // Helpers (mirrors AlertPersistenceIntegrationTest's fixture conventions)
    // =====================================================================

    /**
     * Commits fixtures so a later {@code Propagation.REQUIRES_NEW} call
     * (AlertInsertExecutor.insertAlert, invoked transitively via
     * {@code DetectionEngine.evaluate() -> AlertService.createAlertFrom})
     * can see them - see {@code AlertPersistenceIntegrationTest.commitFixtures()}
     * for the full rationale, which applies identically here.
     */
    private void commitFixtures() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
    }

    private List<Alert> allAlertsFor(UUID userId, UUID ruleId) {
        return alertRepository.findAll().stream()
            .filter(a -> a.getUser() != null && userId.equals(a.getUser().getId()))
            .filter(a -> a.getRule() != null && ruleId.equals(a.getRule().getId()))
            .toList();
    }

    private DetectionRule persistRule() {
        DetectionRule rule = new DetectionRule();
        rule.setName("IT RiskScore Repeated Failed Login " + UUID.randomUUID());
        rule.setDescription("Integration-test-only rule; rolled back after the test.");
        rule.setRuleType(DetectionRule.RuleType.THRESHOLD);
        rule.setEventSource("AUTH_FAILURE");
        rule.setThreshold(THRESHOLD);
        rule.setWindowSeconds(WINDOW_SECONDS);
        rule.setSeverity(DetectionRule.Severity.HIGH);
        rule.setEnabled(true);
        DetectionRule saved = detectionRuleRepository.save(rule);
        createdRuleIds.add(saved.getId());
        return saved;
    }

    private User persistUser() {
        String suffix = UUID.randomUUID().toString();
        User user = new User();
        user.setUsername("it-riskscore-user-" + suffix);
        user.setEmail("it-riskscore-user-" + suffix + "@example.invalid");
        user.setPasswordHash("integration-test-placeholder-hash");
        user.setFullName("Integration Test User");
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private EndpointDevice persistEndpoint() {
        EndpointDevice endpoint = new EndpointDevice();
        endpoint.setHostname("IT-RISKSCORE-ENDPOINT-" + UUID.randomUUID());
        endpoint.setMacAddress(randomMacAddress());
        endpoint.setAgentTokenHash(randomHex(64));
        EndpointDevice saved = endpointDeviceRepository.save(endpoint);
        createdEndpointIds.add(saved.getId());
        return saved;
    }

    /** Persists {@code count} AuthFailureEvent rows for {@code user}, all
     * with {@code attemptedAt} well within WINDOW_SECONDS of "now" - enough
     * for RepeatedFailedLoginDetector's countByUser_IdAndAttemptedAtAfter
     * query to meet THRESHOLD. */
    private void persistFailureEvents(User user, int count) {
        for (int i = 0; i < count; i++) {
            AuthFailureEvent event = new AuthFailureEvent();
            event.setUser(user);
            event.setSourceIp("127.0.0.1");
            event.setAttemptedAt(Instant.now().minusSeconds(i));
            authFailureEventRepository.save(event);
        }
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
