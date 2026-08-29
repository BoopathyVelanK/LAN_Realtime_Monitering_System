package com.securesoc.integration;

import com.securesoc.entity.Alert;
import com.securesoc.entity.AuthFailureEvent;
import com.securesoc.entity.DetectionRule;
import com.securesoc.entity.User;
import com.securesoc.exception.UnauthorizedException;
import com.securesoc.repository.AlertRepository;
import com.securesoc.repository.AuthFailureEventRepository;
import com.securesoc.repository.DetectionRuleRepository;
import com.securesoc.repository.RiskScoreRepository;
import com.securesoc.repository.UserRepository;
import com.securesoc.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end proof of the Checkpoint E wiring - {@code POST /auth/login}
 * failed password -&gt; {@code AuthFailureEvent} -&gt; {@code DetectionEngine
 * .evaluate()} -&gt; {@code AlertService}/{@code RiskScoreService} - driven
 * through the real, Spring-wired {@link AuthService#login} entry point
 * against a real PostgreSQL instance (Testcontainers), not a hand-built
 * {@code DetectionContext} and not H2. Follows the same
 * container/fixture-commit/cleanup conventions as
 * {@code RiskScorePersistenceIntegrationTest} and
 * {@code AlertPersistenceIntegrationTest} (see those classes' javadoc for
 * the full rationale, which applies unchanged here).
 *
 * This class specifically exercises two things neither of those classes
 * covers, because both predate Checkpoint E and call
 * {@code DetectionEngine.evaluate()} directly:
 * <ul>
 *   <li>That a real failed login (via {@code AuthService.login()}, not a
 *   hand-built context) now actually reaches {@code DetectionEngine} at
 *   all - this was Checkpoint E's entire purpose (see the prior design
 *   review: before this wiring, nothing in production called
 *   {@code evaluate()}).</li>
 *   <li>That {@code AuthFailureRecorder}'s {@code REQUIRES_NEW} persistence
 *   survives {@code login()}'s own transaction being rolled back by the
 *   {@code UnauthorizedException} it throws - i.e. that the failed-attempt
 *   counter, lockout, and {@code AuthFailureEvent} rows are genuinely
 *   durable now, not silently discarded as they were before Checkpoint E.</li>
 * </ul>
 *
 * The absence of any {@code RiskScore} row in these assertions is
 * deliberate, not an oversight: a portal login has no schema-level
 * relationship to any {@code EndpointDevice} (see {@code AuthFailureRecorder}
 * javadoc), so {@code endpointId} is always {@code null} for an
 * {@code AUTH_FAILURE} detection, which {@code RiskScoreService} already
 * treats as a no-op. This test asserts that limitation explicitly so a
 * future schema change that resolves it will fail this test loudly instead
 * of the gap going unnoticed again.
 */
@Testcontainers
@SpringBootTest
@Transactional
class AuthFailureDetectionIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AuthService authService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Autowired
    private DetectionRuleRepository detectionRuleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthFailureEventRepository authFailureEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final int THRESHOLD = 5;
    private static final int WINDOW_SECONDS = 300;
    private static final String CORRECT_PASSWORD = "CorrectHorseBatteryStaple123!";
    private static final String WRONG_PASSWORD = "definitely-wrong-password";
    private static final String SOURCE_IP = "203.0.113.10";

    // Same rationale as RiskScorePersistenceIntegrationTest: commitFixtures()
    // permanently commits these rows, so they must be tracked and explicitly
    // deleted in @AfterEach rather than relying on the test's own
    // @Transactional rollback, which never covers them.
    private final Set<UUID> createdRuleIds = new LinkedHashSet<>();
    private final Set<UUID> createdUserIds = new LinkedHashSet<>();

    @Test
    void fiveFailedLogins_reachThreshold_persistAllEventsDurably_andCreateOneAlert_butNoRiskScore() {
        DetectionRule rule = persistRule();
        User user = persistUser();
        commitFixtures();

        long riskScoreCountBefore = riskScoreRepository.count();

        for (int i = 0; i < THRESHOLD; i++) {
            UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> authService.login(user.getUsername(), WRONG_PASSWORD, SOURCE_IP));
            assertEquals("Invalid username/email or password", ex.getMessage());
        }

        // Finding B, fixed: every failed attempt's AuthFailureEvent row is
        // now durable, despite login() throwing UnauthorizedException (and
        // therefore rolling back its own transaction) on every single call.
        List<AuthFailureEvent> events =
            authFailureEventRepository.findByUser_IdAndAttemptedAtAfterOrderByAttemptedAtDesc(
                user.getId(), Instant.now().minusSeconds(WINDOW_SECONDS));
        assertEquals(THRESHOLD, events.size(),
            "All " + THRESHOLD + " AuthFailureEvent rows must survive login()'s own rollback");

        // Existing lockout behavior, also now durable for the same reason.
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(THRESHOLD, reloaded.getFailedLoginAttempts());
        assertNotNull(reloaded.getLockedUntil());
        assertTrue(reloaded.getLockedUntil().isAfter(Instant.now()));

        // Checkpoint E's actual point: DetectionEngine.evaluate() was reached
        // from the real login() path, and AlertService's existing behavior
        // (unmodified by this checkpoint) still creates exactly one OPEN alert.
        List<Alert> alerts = allAlertsFor(user.getId(), rule.getId());
        assertEquals(1, alerts.size(), "Threshold met on the 5th attempt - exactly one Alert expected");
        assertEquals(Alert.Status.OPEN, alerts.get(0).getStatus());

        // Finding A: endpointId is always null for a portal-login detection,
        // so RiskScoreService.recordDetection() is a no-op end-to-end here.
        assertEquals(riskScoreCountBefore, riskScoreRepository.count(),
            "A portal login has no EndpointDevice relationship, so no RiskScore row should ever appear here");
    }

    @Test
    void fewerThanThresholdFailedLogins_persistEventsDurably_butCreateNoAlert() {
        DetectionRule rule = persistRule();
        User user = persistUser();
        commitFixtures();

        int belowThreshold = THRESHOLD - 1;
        for (int i = 0; i < belowThreshold; i++) {
            assertThrows(UnauthorizedException.class,
                () -> authService.login(user.getUsername(), WRONG_PASSWORD, SOURCE_IP));
        }

        // Durable persistence (Finding B fix) does not depend on the
        // threshold being met - every attempt still records its event.
        List<AuthFailureEvent> events =
            authFailureEventRepository.findByUser_IdAndAttemptedAtAfterOrderByAttemptedAtDesc(
                user.getId(), Instant.now().minusSeconds(WINDOW_SECONDS));
        assertEquals(belowThreshold, events.size());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(belowThreshold, reloaded.getFailedLoginAttempts());

        // Detection must still be threshold-gated through the real
        // AuthService path, not fire on every single attempt.
        List<Alert> alerts = allAlertsFor(user.getId(), rule.getId());
        assertTrue(alerts.isEmpty(), "Below threshold - no Alert should be created yet");
    }

    @Test
    void successfulLoginAfterFailures_clearsCounterAndLockout_createsNoNewAlert() {
        DetectionRule rule = persistRule();
        User user = persistUser();
        commitFixtures();

        assertThrows(UnauthorizedException.class,
            () -> authService.login(user.getUsername(), WRONG_PASSWORD, SOURCE_IP));
        assertThrows(UnauthorizedException.class,
            () -> authService.login(user.getUsername(), WRONG_PASSWORD, SOURCE_IP));

        // Existing behavior, unmodified: a subsequent successful login
        // clears the failure streak (verified via the real password hash).
        authService.login(user.getUsername(), CORRECT_PASSWORD, SOURCE_IP);

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(0, reloaded.getFailedLoginAttempts());
        assertFalse(reloaded.getLockedUntil() != null && reloaded.getLockedUntil().isAfter(Instant.now()));

        // Only 2 failures ever occurred - still below the 5-event threshold.
        List<Alert> alerts = allAlertsFor(user.getId(), rule.getId());
        assertTrue(alerts.isEmpty(), "Only 2 failed attempts occurred - below threshold, so no Alert expected");
    }

    // =====================================================================
    // Cleanup (mirrors RiskScorePersistenceIntegrationTest's convention)
    // =====================================================================

    @AfterEach
    void cleanUpCommittedFixtures() {
        if (!TestTransaction.isActive()) {
            TestTransaction.start();
        }

        alertRepository.findAll().stream()
            .filter(a -> (a.getUser() != null && createdUserIds.contains(a.getUser().getId()))
                || (a.getRule() != null && createdRuleIds.contains(a.getRule().getId())))
            .forEach(alertRepository::delete);
        alertRepository.flush();

        authFailureEventRepository.findAll().stream()
            .filter(event -> event.getUser() != null && createdUserIds.contains(event.getUser().getId()))
            .forEach(authFailureEventRepository::delete);
        authFailureEventRepository.flush();

        createdUserIds.forEach(userRepository::deleteById);
        userRepository.flush();

        createdRuleIds.forEach(detectionRuleRepository::deleteById);
        detectionRuleRepository.flush();

        TestTransaction.flagForCommit();
        TestTransaction.end();

        createdRuleIds.clear();
        createdUserIds.clear();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** Same rationale as RiskScorePersistenceIntegrationTest.commitFixtures():
     * a later Propagation.REQUIRES_NEW call (AuthFailureRecorder.recordFailure,
     * and transitively AlertInsertExecutor.insertAlert) needs these fixtures
     * to already be committed and visible in its own, separate transaction. */
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
        rule.setName("IT AuthFailureDetection Repeated Failed Login " + UUID.randomUUID());
        rule.setDescription("Integration-test-only rule for Checkpoint E; deleted in @AfterEach.");
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
        user.setUsername("it-authfailure-user-" + suffix);
        user.setEmail("it-authfailure-user-" + suffix + "@example.invalid");
        user.setPasswordHash(passwordEncoder.encode(CORRECT_PASSWORD));
        user.setFullName("Integration Test User");
        user.setEnabled(true);
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }
}
