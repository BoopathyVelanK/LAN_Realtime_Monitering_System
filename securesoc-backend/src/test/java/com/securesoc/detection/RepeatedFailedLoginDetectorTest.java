package com.securesoc.detection;

import com.securesoc.entity.DetectionRule;
import com.securesoc.repository.AuthFailureEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepeatedFailedLoginDetectorTest {

    @Mock
    private AuthFailureEventRepository authFailureEventRepository;

    private RepeatedFailedLoginDetector detector;

    private UUID userId;
    private UUID endpointId;
    private Instant occurredAt;

    @BeforeEach
    void setUp() {
        detector = new RepeatedFailedLoginDetector(authFailureEventRepository);
        userId = UUID.randomUUID();
        endpointId = UUID.randomUUID();
        occurredAt = Instant.parse("2026-01-01T00:00:00Z");
    }

    private DetectionRule supportedRule(int threshold, int windowSeconds) {
        DetectionRule rule = new DetectionRule();
        rule.setId(UUID.randomUUID());
        rule.setName("Repeated failed login");
        rule.setRuleType(DetectionRule.RuleType.THRESHOLD);
        rule.setEventSource("AUTH_FAILURE");
        rule.setThreshold(threshold);
        rule.setWindowSeconds(windowSeconds);
        rule.setSeverity(DetectionRule.Severity.HIGH);
        rule.setEnabled(true);
        return rule;
    }

    private DetectionContext contextWith(UUID userId, Instant occurredAt) {
        return new DetectionContext("AUTH_FAILURE", endpointId, userId, occurredAt, null);
    }

    @Test
    void supports_returnsTrueForThresholdAuthFailureRule() {
        assertTrue(detector.supports(supportedRule(5, 300)));
    }

    @Test
    void supports_returnsFalseForDifferentEventSource() {
        DetectionRule rule = supportedRule(5, 300);
        rule.setEventSource("USB_EVENT");
        assertFalse(detector.supports(rule));
    }

    @Test
    void supports_returnsFalseForNullRule() {
        assertFalse(detector.supports(null));
    }

    @Test
    void evaluate_thresholdNotReached_returnsNotDetected() {
        DetectionRule rule = supportedRule(5, 300);
        when(authFailureEventRepository.countByUser_IdAndAttemptedAtAfter(eq(userId), any(Instant.class)))
            .thenReturn(4L);

        DetectionResult result = detector.evaluate(contextWith(userId, occurredAt), rule);

        assertFalse(result.detected());
    }

    @Test
    void evaluate_thresholdExactlyReached_returnsDetected() {
        DetectionRule rule = supportedRule(5, 300);
        when(authFailureEventRepository.countByUser_IdAndAttemptedAtAfter(eq(userId), any(Instant.class)))
            .thenReturn(5L);

        DetectionResult result = detector.evaluate(contextWith(userId, occurredAt), rule);

        assertTrue(result.detected());
        assertEquals(rule.getId(), result.ruleId());
        assertEquals(rule.getSeverity(), result.severity());
        assertEquals(userId, result.userId());
        assertEquals(endpointId, result.endpointId());
        assertNotNull(result.title());
        assertNotNull(result.description());
    }

    @Test
    void evaluate_thresholdExceeded_returnsDetected() {
        DetectionRule rule = supportedRule(5, 300);
        when(authFailureEventRepository.countByUser_IdAndAttemptedAtAfter(eq(userId), any(Instant.class)))
            .thenReturn(9L);

        DetectionResult result = detector.evaluate(contextWith(userId, occurredAt), rule);

        assertTrue(result.detected());
    }

    @Test
    void evaluate_nullContext_returnsNotDetected() {
        DetectionRule rule = supportedRule(5, 300);

        DetectionResult result = detector.evaluate(null, rule);

        assertFalse(result.detected());
        verifyNoInteractions(authFailureEventRepository);
    }

    @Test
    void evaluate_nullUserId_returnsNotDetected() {
        DetectionRule rule = supportedRule(5, 300);

        DetectionResult result = detector.evaluate(contextWith(null, occurredAt), rule);

        assertFalse(result.detected());
    }

    @Test
    void evaluate_nullThreshold_returnsNotDetected() {
        DetectionRule rule = supportedRule(5, 300);
        rule.setThreshold(null);

        DetectionResult result = detector.evaluate(contextWith(userId, occurredAt), rule);

        assertFalse(result.detected());
    }

    @Test
    void evaluate_nonPositiveThreshold_returnsNotDetected() {
        DetectionRule rule = supportedRule(0, 300);

        DetectionResult result = detector.evaluate(contextWith(userId, occurredAt), rule);

        assertFalse(result.detected());
    }

    @Test
    void evaluate_nullWindowSeconds_returnsNotDetected() {
        DetectionRule rule = supportedRule(5, 300);
        rule.setWindowSeconds(null);

        DetectionResult result = detector.evaluate(contextWith(userId, occurredAt), rule);

        assertFalse(result.detected());
    }

    @Test
    void evaluate_nonPositiveWindowSeconds_returnsNotDetected() {
        DetectionRule rule = supportedRule(5, 0);

        DetectionResult result = detector.evaluate(contextWith(userId, occurredAt), rule);

        assertFalse(result.detected());
    }

    @Test
    void evaluate_nullOccurredAt_returnsNotDetected() {
        DetectionRule rule = supportedRule(5, 300);

        DetectionResult result = detector.evaluate(contextWith(userId, null), rule);

        assertFalse(result.detected());
    }

    @Test
    void evaluate_unsupportedRule_returnsNotDetected() {
        DetectionRule rule = supportedRule(5, 300);
        rule.setRuleType(null);

        DetectionResult result = detector.evaluate(contextWith(userId, occurredAt), rule);

        assertFalse(result.detected());
    }
    @Test
    void evaluate_passesOccurredAtMinusWindowSecondsAsSinceBoundary() {
        Instant fixedOccurredAt = Instant.parse("2026-01-01T12:00:00Z");
        Instant expectedSince = Instant.parse("2026-01-01T11:55:00Z");
        DetectionRule rule = supportedRule(5, 300);
        when(authFailureEventRepository.countByUser_IdAndAttemptedAtAfter(userId, expectedSince))
            .thenReturn(5L);

        DetectionResult result = detector.evaluate(contextWith(userId, fixedOccurredAt), rule);

        ArgumentCaptor<UUID> userCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(authFailureEventRepository)
            .countByUser_IdAndAttemptedAtAfter(userCaptor.capture(), sinceCaptor.capture());
        verifyNoMoreInteractions(authFailureEventRepository);

        assertEquals(userId, userCaptor.getValue());
        assertEquals(expectedSince, sinceCaptor.getValue());
        assertTrue(result.detected());
    }

    @Test
    void evaluate_differentWindowSeconds_producesDifferentSinceBoundary() {
        Instant fixedOccurredAt = Instant.parse("2026-01-01T12:00:00Z");
        Instant sinceFor300 = Instant.parse("2026-01-01T11:55:00Z");
        Instant sinceFor900 = Instant.parse("2026-01-01T11:45:00Z");

        when(authFailureEventRepository.countByUser_IdAndAttemptedAtAfter(userId, sinceFor300))
            .thenReturn(1L);
        when(authFailureEventRepository.countByUser_IdAndAttemptedAtAfter(userId, sinceFor900))
            .thenReturn(1L);

        detector.evaluate(contextWith(userId, fixedOccurredAt), supportedRule(5, 300));
        detector.evaluate(contextWith(userId, fixedOccurredAt), supportedRule(5, 900));

        assertNotEquals(sinceFor300, sinceFor900);
        verify(authFailureEventRepository)
            .countByUser_IdAndAttemptedAtAfter(userId, sinceFor300);
        verify(authFailureEventRepository)
            .countByUser_IdAndAttemptedAtAfter(userId, sinceFor900);
        verifyNoMoreInteractions(authFailureEventRepository);
    }
}
