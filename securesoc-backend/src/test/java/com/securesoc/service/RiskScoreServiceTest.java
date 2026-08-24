package com.securesoc.service;

import com.securesoc.detection.DetectionResult;
import com.securesoc.entity.DetectionRule;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.RiskScore;
import com.securesoc.exception.ResourceNotFoundException;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.repository.RiskScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskScoreServiceTest {

    @Mock
    private RiskScoreRepository riskScoreRepository;
    @Mock
    private EndpointDeviceRepository endpointDeviceRepository;

    private RiskScoreService riskScoreService;

    private UUID ruleId;
    private UUID userId;
    private UUID endpointId;

    @BeforeEach
    void setUp() {
        riskScoreService = new RiskScoreService(riskScoreRepository, endpointDeviceRepository);
        ruleId = UUID.randomUUID();
        userId = UUID.randomUUID();
        endpointId = UUID.randomUUID();
    }

    private DetectionResult detectedResult(DetectionRule.Severity severity, UUID endpointId) {
        return new DetectionResult(
            true,
            ruleId,
            severity,
            "Some detection",
            "Some description",
            userId,
            endpointId
        );
    }

    private EndpointDevice endpoint() {
        EndpointDevice endpoint = new EndpointDevice();
        endpoint.setId(endpointId);
        return endpoint;
    }

    private RiskScore riskScoreWith(short score, RiskScore.Level level) {
        RiskScore riskScore = new RiskScore();
        riskScore.setEndpoint(endpoint());
        riskScore.setScore(score);
        riskScore.setLevel(level);
        return riskScore;
    }

    // =====================================================================
    // No-op cases
    // =====================================================================

    @Test
    void recordDetection_null_returnsEmptyAndTouchesNothing() {
        Optional<RiskScore> result = riskScoreService.recordDetection(null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(riskScoreRepository, endpointDeviceRepository);
    }

    @Test
    void recordDetection_none_returnsEmptyAndTouchesNothing() {
        Optional<RiskScore> result = riskScoreService.recordDetection(DetectionResult.none());

        assertTrue(result.isEmpty());
        verifyNoInteractions(riskScoreRepository, endpointDeviceRepository);
    }

    @Test
    void recordDetection_notDetected_returnsEmptyAndTouchesNothing() {
        DetectionResult notDetected = new DetectionResult(
            false, ruleId, DetectionRule.Severity.HIGH, "t", "d", userId, endpointId);

        Optional<RiskScore> result = riskScoreService.recordDetection(notDetected);

        assertTrue(result.isEmpty());
        verifyNoInteractions(riskScoreRepository, endpointDeviceRepository);
    }

    @Test
    void recordDetection_nullEndpointId_returnsEmptyAndTouchesNothing() {
        Optional<RiskScore> result = riskScoreService.recordDetection(
            detectedResult(DetectionRule.Severity.HIGH, null));

        assertTrue(result.isEmpty());
        verifyNoInteractions(riskScoreRepository, endpointDeviceRepository);
    }

    // =====================================================================
    // Severity -> points mapping
    // =====================================================================

    @ParameterizedTest
    @CsvSource({
        "LOW, 5",
        "MEDIUM, 15",
        "HIGH, 30",
        "CRITICAL, 50",
    })
    void recordDetection_newEndpoint_startsFromZeroPlusSeverityPoints(DetectionRule.Severity severity, int expectedPoints) {
        when(riskScoreRepository.findByEndpoint_Id(endpointId)).thenReturn(Optional.empty());
        when(endpointDeviceRepository.findById(endpointId)).thenReturn(Optional.of(endpoint()));
        when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<RiskScore> result = riskScoreService.recordDetection(detectedResult(severity, endpointId));

        assertTrue(result.isPresent());
        assertEquals((short) expectedPoints, result.get().getScore());
    }

    @Test
    void recordDetection_nullSeverity_failsLoudRatherThanScoringZero() {
        // Severity is guaranteed non-null for any detected() == true result
        // (DetectionRule.severity is NOT NULL, and the sole construction
        // site - RepeatedFailedLoginDetector - always passes
        // rule.getSeverity() straight through). A null severity here would
        // mean that invariant was violated elsewhere, so this documents
        // that the service fails loud instead of silently recording a
        // zero-point risk event.
        when(riskScoreRepository.findByEndpoint_Id(endpointId)).thenReturn(Optional.empty());
        when(endpointDeviceRepository.findById(endpointId)).thenReturn(Optional.of(endpoint()));

        assertThrows(NullPointerException.class,
            () -> riskScoreService.recordDetection(detectedResult(null, endpointId)));
    }

    // =====================================================================
    // Existing row is updated in place, not duplicated
    // =====================================================================

    @Test
    void recordDetection_existingEndpoint_updatesExistingRowInPlace() {
        RiskScore existing = riskScoreWith((short) 20, RiskScore.Level.LOW);
        when(riskScoreRepository.findByEndpoint_Id(endpointId)).thenReturn(Optional.of(existing));
        when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<RiskScore> result = riskScoreService.recordDetection(
            detectedResult(DetectionRule.Severity.MEDIUM, endpointId));

        assertTrue(result.isPresent());
        assertSame(existing, result.get());
        assertEquals((short) 35, result.get().getScore());
        assertEquals(RiskScore.Level.MEDIUM, result.get().getLevel());
        verifyNoInteractions(endpointDeviceRepository);
    }

    @Test
    void recordDetection_newEndpoint_resolvesEndpointThroughRepository() {
        when(riskScoreRepository.findByEndpoint_Id(endpointId)).thenReturn(Optional.empty());
        when(endpointDeviceRepository.findById(endpointId)).thenReturn(Optional.of(endpoint()));
        ArgumentCaptor<RiskScore> captor = ArgumentCaptor.forClass(RiskScore.class);
        when(riskScoreRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        riskScoreService.recordDetection(detectedResult(DetectionRule.Severity.LOW, endpointId));

        assertEquals(endpointId, captor.getValue().getEndpoint().getId());
        verify(endpointDeviceRepository).findById(endpointId);
    }

    @Test
    void recordDetection_missingEndpoint_throwsResourceNotFoundExceptionAndDoesNotSave() {
        when(riskScoreRepository.findByEndpoint_Id(endpointId)).thenReturn(Optional.empty());
        when(endpointDeviceRepository.findById(endpointId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> riskScoreService.recordDetection(detectedResult(DetectionRule.Severity.HIGH, endpointId)));

        verify(riskScoreRepository, never()).save(any(RiskScore.class));
    }

    // =====================================================================
    // Clamping
    // =====================================================================

    @Test
    void recordDetection_clampsAtMaxScoreOf100() {
        RiskScore existing = riskScoreWith((short) 90, RiskScore.Level.CRITICAL);
        when(riskScoreRepository.findByEndpoint_Id(endpointId)).thenReturn(Optional.of(existing));
        when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<RiskScore> result = riskScoreService.recordDetection(
            detectedResult(DetectionRule.Severity.CRITICAL, endpointId));

        assertTrue(result.isPresent());
        assertEquals((short) 100, result.get().getScore());
        assertEquals(RiskScore.Level.CRITICAL, result.get().getLevel());
    }

    @Test
    void recordDetection_scoreNeverExceedsClampEvenFromMaxStartingPoint() {
        RiskScore existing = riskScoreWith((short) 100, RiskScore.Level.CRITICAL);
        when(riskScoreRepository.findByEndpoint_Id(endpointId)).thenReturn(Optional.of(existing));
        when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<RiskScore> result = riskScoreService.recordDetection(
            detectedResult(DetectionRule.Severity.LOW, endpointId));

        assertTrue(result.isPresent());
        assertEquals((short) 100, result.get().getScore());
    }

    // =====================================================================
    // Level thresholds (SAFE < 15, LOW 15-34, MEDIUM 35-59, HIGH 60-79, CRITICAL 80-100)
    // Uses a MEDIUM (+15) detection on top of a pre-set existing score so the
    // exact resulting score - and therefore the level boundary under test -
    // is controlled directly, independent of the points-accumulation logic
    // already covered above.
    // =====================================================================

    @ParameterizedTest
    @CsvSource({
        // preScore, expectedPostScore, expectedLevel
        "-15, 0, SAFE",
        "-1, 14, SAFE",
        "0, 15, LOW",
        "19, 34, LOW",
        "20, 35, MEDIUM",
        "44, 59, MEDIUM",
        "45, 60, HIGH",
        "64, 79, HIGH",
        "65, 80, CRITICAL",
        "85, 100, CRITICAL",
    })
    void recordDetection_levelMatchesScoreBoundary(short preScore, short expectedPostScore, RiskScore.Level expectedLevel) {
        RiskScore existing = riskScoreWith(preScore, RiskScore.Level.SAFE);
        when(riskScoreRepository.findByEndpoint_Id(endpointId)).thenReturn(Optional.of(existing));
        when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<RiskScore> result = riskScoreService.recordDetection(
            detectedResult(DetectionRule.Severity.MEDIUM, endpointId));

        assertTrue(result.isPresent());
        assertEquals(expectedPostScore, result.get().getScore());
        assertEquals(expectedLevel, result.get().getLevel());
    }

    // =====================================================================
    // Additive scoring, independent of alert deduplication
    // =====================================================================

    @Test
    void recordDetection_repeatedDetectionsForSameEndpoint_accumulateAdditively() {
        RiskScore riskScore = riskScoreWith((short) 0, RiskScore.Level.SAFE);
        when(riskScoreRepository.findByEndpoint_Id(endpointId)).thenReturn(Optional.of(riskScore));
        when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));

        riskScoreService.recordDetection(detectedResult(DetectionRule.Severity.HIGH, endpointId));
        riskScoreService.recordDetection(detectedResult(DetectionRule.Severity.HIGH, endpointId));

        assertEquals((short) 60, riskScore.getScore());
        assertEquals(RiskScore.Level.HIGH, riskScore.getLevel());
        verify(riskScoreRepository, times(2)).save(any(RiskScore.class));
    }

    @Test
    void recordDetection_differentEndpoints_scoredIndependently() {
        UUID endpointIdB = UUID.randomUUID();
        EndpointDevice endpointB = new EndpointDevice();
        endpointB.setId(endpointIdB);

        when(riskScoreRepository.findByEndpoint_Id(endpointId)).thenReturn(Optional.empty());
        when(riskScoreRepository.findByEndpoint_Id(endpointIdB)).thenReturn(Optional.empty());
        when(endpointDeviceRepository.findById(endpointId)).thenReturn(Optional.of(endpoint()));
        when(endpointDeviceRepository.findById(endpointIdB)).thenReturn(Optional.of(endpointB));
        when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<RiskScore> resultA = riskScoreService.recordDetection(
            detectedResult(DetectionRule.Severity.CRITICAL, endpointId));
        Optional<RiskScore> resultB = riskScoreService.recordDetection(
            detectedResult(DetectionRule.Severity.LOW, endpointIdB));

        assertEquals((short) 50, resultA.get().getScore());
        assertEquals((short) 5, resultB.get().getScore());
        assertEquals(endpointId, resultA.get().getEndpoint().getId());
        assertEquals(endpointIdB, resultB.get().getEndpoint().getId());
    }

    // =====================================================================
    // No decay: nothing in this class ever reduces score other than the
    // clamp ceiling - there is no method/path that lowers an existing score.
    // =====================================================================

    @Test
    void recordDetection_scoreOnlyIncreasesOrStaysSame_neverDecreases() {
        RiskScore existing = riskScoreWith((short) 50, RiskScore.Level.MEDIUM);
        when(riskScoreRepository.findByEndpoint_Id(endpointId)).thenReturn(Optional.of(existing));
        when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<RiskScore> result = riskScoreService.recordDetection(
            detectedResult(DetectionRule.Severity.LOW, endpointId));

        assertTrue(result.get().getScore() >= 50);
    }

    // =====================================================================
    // Every DetectionRule.Severity value is mapped (defensive against enum drift)
    // =====================================================================

    @ParameterizedTest
    @EnumSource(DetectionRule.Severity.class)
    void recordDetection_everySeverityValueProducesNonNegativePoints(DetectionRule.Severity severity) {
        when(riskScoreRepository.findByEndpoint_Id(endpointId)).thenReturn(Optional.empty());
        when(endpointDeviceRepository.findById(endpointId)).thenReturn(Optional.of(endpoint()));
        when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<RiskScore> result = riskScoreService.recordDetection(detectedResult(severity, endpointId));

        assertTrue(result.isPresent());
        assertTrue(result.get().getScore() >= 0);
    }
}
