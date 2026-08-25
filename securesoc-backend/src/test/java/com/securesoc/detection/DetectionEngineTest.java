package com.securesoc.detection;

import com.securesoc.entity.DetectionRule;
import com.securesoc.repository.DetectionRuleRepository;
import com.securesoc.service.AlertService;
import com.securesoc.service.RiskScoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DetectionEngineTest {

    @Mock
    private DetectionRuleRepository detectionRuleRepository;
    @Mock
    private AlertService alertService;
    @Mock
    private RiskScoreService riskScoreService;
    @Mock
    private Detector detectorA;
    @Mock
    private Detector detectorB;

    private UUID endpointId;
    private UUID userId;
    private Instant occurredAt;

    @BeforeEach
    void setUp() {
        endpointId = UUID.randomUUID();
        userId = UUID.randomUUID();
        occurredAt = Instant.parse("2026-01-01T00:00:00Z");
    }

    private DetectionRule rule() {
        DetectionRule rule = new DetectionRule();
        rule.setId(UUID.randomUUID());
        rule.setName("Repeated failed login");
        rule.setRuleType(DetectionRule.RuleType.THRESHOLD);
        rule.setEventSource("AUTH_FAILURE");
        rule.setSeverity(DetectionRule.Severity.HIGH);
        rule.setEnabled(true);
        return rule;
    }

    private DetectionContext context() {
        return new DetectionContext("AUTH_FAILURE", endpointId, userId, occurredAt, null);
    }

    private DetectionEngine engine() {
        return new DetectionEngine(detectionRuleRepository, List.of(detectorA, detectorB), alertService, riskScoreService);
    }

    // --- detected result is passed to AlertService ---

    @Test
    void evaluate_detectedResult_isPassedToAlertService() {
        DetectionRule rule = rule();
        DetectionResult detected = new DetectionResult(
            true, rule.getId(), DetectionRule.Severity.HIGH, "title", "desc", userId, endpointId);

        when(detectionRuleRepository.findByEventSourceAndEnabledTrue("AUTH_FAILURE"))
            .thenReturn(List.of(rule));
        when(detectorA.supports(rule)).thenReturn(true);
        when(detectorB.supports(rule)).thenReturn(false);
        when(detectorA.evaluate(any(DetectionContext.class), eq(rule))).thenReturn(detected);

        List<DetectionResult> results = engine().evaluate(context());

        assertEquals(1, results.size());
        assertTrue(results.get(0).detected());
        verify(alertService, times(1)).createAlertFrom(detected);
    }

    // --- detected result is passed to RiskScoreService ---

    @Test
    void evaluate_detectedResult_isPassedToRiskScoreService() {
        DetectionRule rule = rule();
        DetectionResult detected = new DetectionResult(
            true, rule.getId(), DetectionRule.Severity.HIGH, "title", "desc", userId, endpointId);

        when(detectionRuleRepository.findByEventSourceAndEnabledTrue("AUTH_FAILURE"))
            .thenReturn(List.of(rule));
        when(detectorA.supports(rule)).thenReturn(true);
        when(detectorB.supports(rule)).thenReturn(false);
        when(detectorA.evaluate(any(DetectionContext.class), eq(rule))).thenReturn(detected);

        engine().evaluate(context());

        verify(riskScoreService, times(1)).recordDetection(detected);
    }

    // --- non-detected result is NOT passed to AlertService or RiskScoreService ---

    @Test
    void evaluate_nonDetectedResult_isNotPassedToAlertServiceOrRiskScoreService() {
        DetectionRule rule = rule();
        DetectionResult none = DetectionResult.none();

        when(detectionRuleRepository.findByEventSourceAndEnabledTrue("AUTH_FAILURE"))
            .thenReturn(List.of(rule));
        when(detectorA.supports(rule)).thenReturn(true);
        when(detectorB.supports(rule)).thenReturn(false);
        when(detectorA.evaluate(any(DetectionContext.class), eq(rule))).thenReturn(none);

        List<DetectionResult> results = engine().evaluate(context());

        assertTrue(results.isEmpty());
        verifyNoInteractions(alertService);
        verifyNoInteractions(riskScoreService);
    }

    @Test
    void evaluate_mixOfDetectedAndNotDetected_onlyDetectedPassedToAlertServiceAndRiskScoreService() {
        DetectionRule ruleDetected = rule();
        DetectionRule ruleNotDetected = rule();

        DetectionResult detected = new DetectionResult(
            true, ruleDetected.getId(), DetectionRule.Severity.HIGH, "t", "d", userId, endpointId);
        DetectionResult notDetected = DetectionResult.none();

        when(detectionRuleRepository.findByEventSourceAndEnabledTrue("AUTH_FAILURE"))
            .thenReturn(List.of(ruleDetected, ruleNotDetected));
        when(detectorA.supports(ruleDetected)).thenReturn(true);
        when(detectorA.supports(ruleNotDetected)).thenReturn(true);
        when(detectorB.supports(any(DetectionRule.class))).thenReturn(false);
        when(detectorA.evaluate(any(DetectionContext.class), eq(ruleDetected))).thenReturn(detected);
        when(detectorA.evaluate(any(DetectionContext.class), eq(ruleNotDetected))).thenReturn(notDetected);

        List<DetectionResult> results = engine().evaluate(context());

        assertEquals(1, results.size());
        verify(alertService, times(1)).createAlertFrom(detected);
        verify(alertService, never()).createAlertFrom(notDetected);
        verify(riskScoreService, times(1)).recordDetection(detected);
        verify(riskScoreService, never()).recordDetection(notDetected);
    }

    // --- RiskScoreService is called unconditionally, independent of what AlertService returns ---

    @Test
    void evaluate_riskScoringHappensRegardlessOfAlertServiceOutcome() {
        // Simulates AlertService internally skipping/deduplicating (e.g.
        // returning Optional.empty() because userId was null - see
        // AlertService's own dedup-skip behavior) - DetectionEngine must
        // not read or branch on that return value before calling
        // RiskScoreService. It doesn't use the return value at all today,
        // but this test pins that down explicitly so it can't regress.
        DetectionRule rule = rule();
        DetectionResult detected = new DetectionResult(
            true, rule.getId(), DetectionRule.Severity.HIGH, "title", "desc", userId, endpointId);

        when(detectionRuleRepository.findByEventSourceAndEnabledTrue("AUTH_FAILURE"))
            .thenReturn(List.of(rule));
        when(detectorA.supports(rule)).thenReturn(true);
        when(detectorB.supports(rule)).thenReturn(false);
        when(detectorA.evaluate(any(DetectionContext.class), eq(rule))).thenReturn(detected);
        when(alertService.createAlertFrom(detected)).thenReturn(Optional.empty());

        engine().evaluate(context());

        verify(riskScoreService, times(1)).recordDetection(detected);
    }

    // --- multiple matching detectors still throw AmbiguousDetectorException ---

    @Test
    void evaluate_multipleMatchingDetectors_throwsAmbiguousDetectorExceptionAndSkipsAlertServiceAndRiskScoreService() {
        DetectionRule rule = rule();

        when(detectionRuleRepository.findByEventSourceAndEnabledTrue("AUTH_FAILURE"))
            .thenReturn(List.of(rule));
        when(detectorA.supports(rule)).thenReturn(true);
        when(detectorB.supports(rule)).thenReturn(true);

        assertThrows(AmbiguousDetectorException.class, () -> engine().evaluate(context()));
        verifyNoInteractions(alertService);
        verifyNoInteractions(riskScoreService);
    }

    // --- existing rule filtering behavior remains intact ---

    @Test
    void evaluate_filtersRulesByEventSourceFromContext() {
        when(detectionRuleRepository.findByEventSourceAndEnabledTrue("AUTH_FAILURE"))
            .thenReturn(List.of());

        List<DetectionResult> results = engine().evaluate(context());

        assertTrue(results.isEmpty());
        verify(detectionRuleRepository).findByEventSourceAndEnabledTrue("AUTH_FAILURE");
        verifyNoInteractions(alertService);
        verifyNoInteractions(riskScoreService);
    }

    @Test
    void evaluate_noSupportingDetector_ruleSkippedWithoutErrorOrAlertOrRiskScoring() {
        DetectionRule rule = rule();

        when(detectionRuleRepository.findByEventSourceAndEnabledTrue("AUTH_FAILURE"))
            .thenReturn(List.of(rule));
        when(detectorA.supports(rule)).thenReturn(false);
        when(detectorB.supports(rule)).thenReturn(false);

        List<DetectionResult> results = engine().evaluate(context());

        assertTrue(results.isEmpty());
        verifyNoInteractions(alertService);
        verifyNoInteractions(riskScoreService);
    }
}
