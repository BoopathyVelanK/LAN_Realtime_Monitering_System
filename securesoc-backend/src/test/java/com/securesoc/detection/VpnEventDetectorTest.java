package com.securesoc.detection;

import com.securesoc.entity.DetectionRule;
import com.securesoc.repository.VpnEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VpnEventDetectorTest {

    @Mock
    private VpnEventRepository vpnEventRepository;

    @InjectMocks
    private VpnEventDetector detector;

    private DetectionRule rule;
    private UUID endpointId;
    private UUID ruleId;
    private Instant occurredAt;

    @BeforeEach
    void setUp() {
        ruleId = UUID.randomUUID();
        rule = new DetectionRule();
        rule.setId(ruleId);
        rule.setRuleType(DetectionRule.RuleType.THRESHOLD);
        rule.setEventSource("VPN_EVENT");
        rule.setThreshold(5);
        rule.setWindowSeconds(60);
        rule.setSeverity(DetectionRule.Severity.HIGH);

        endpointId = UUID.randomUUID();
        occurredAt = Instant.parse("2026-08-30T10:00:00Z");
    }

    @Test
    void testNullContext() {
        DetectionResult result = detector.evaluate(null, rule);
        assertThat(result.detected()).isFalse();
        verifyNoInteractions(vpnEventRepository);
    }

    @Test
    void testUnsupportedRuleOrEventSourceReturnsNoDetection() {
        DetectionRule unsupportedRule = new DetectionRule();
        unsupportedRule.setRuleType(DetectionRule.RuleType.THRESHOLD);
        unsupportedRule.setEventSource("OTHER_EVENT");

        DetectionContext context = new DetectionContext("VPN", endpointId, null, occurredAt, null);

        DetectionResult result = detector.evaluate(context, unsupportedRule);
        assertThat(result.detected()).isFalse();
        verifyNoInteractions(vpnEventRepository);
    }

    @Test
    void testMissingEndpointId() {
        DetectionContext context = new DetectionContext("VPN", null, null, occurredAt, null);
        DetectionResult result = detector.evaluate(context, rule);
        assertThat(result.detected()).isFalse();
        verifyNoInteractions(vpnEventRepository);
    }

    @Test
    void testMissingOccurredAt() {
        DetectionContext context = new DetectionContext("VPN", endpointId, null, null, null);
        DetectionResult result = detector.evaluate(context, rule);
        assertThat(result.detected()).isFalse();
        verifyNoInteractions(vpnEventRepository);
    }

    @Test
    void testNullThreshold() {
        rule.setThreshold(null);
        DetectionContext context = new DetectionContext("VPN", endpointId, null, occurredAt, null);

        DetectionResult result = detector.evaluate(context, rule);
        assertThat(result.detected()).isFalse();
        verifyNoInteractions(vpnEventRepository);
    }

    @Test
    void testNonPositiveThreshold() {
        rule.setThreshold(0);
        DetectionContext context = new DetectionContext("VPN", endpointId, null, occurredAt, null);

        DetectionResult result = detector.evaluate(context, rule);
        assertThat(result.detected()).isFalse();
        verifyNoInteractions(vpnEventRepository);
    }

    @Test
    void testNullWindowSeconds() {
        rule.setWindowSeconds(null);
        DetectionContext context = new DetectionContext("VPN", endpointId, null, occurredAt, null);

        DetectionResult result = detector.evaluate(context, rule);
        assertThat(result.detected()).isFalse();
        verifyNoInteractions(vpnEventRepository);
    }

    @Test
    void testNonPositiveWindowSeconds() {
        rule.setWindowSeconds(0);
        DetectionContext context = new DetectionContext("VPN", endpointId, null, occurredAt, null);

        DetectionResult result = detector.evaluate(context, rule);
        assertThat(result.detected()).isFalse();
        verifyNoInteractions(vpnEventRepository);
    }

    @Test
    void testCountBelowThreshold() {
        DetectionContext context = new DetectionContext("VPN", endpointId, null, occurredAt, null);
        Instant expectedSince = occurredAt.minusSeconds(60);
        when(vpnEventRepository.countByEndpoint_IdAndActiveTrueAndDetectedAtAfter(endpointId, expectedSince)).thenReturn(4L);

        DetectionResult result = detector.evaluate(context, rule);
        assertThat(result.detected()).isFalse();
    }

    @Test
    void testCountEqualToThreshold() {
        DetectionContext context = new DetectionContext("VPN", endpointId, null, occurredAt, null);
        Instant expectedSince = occurredAt.minusSeconds(60);
        when(vpnEventRepository.countByEndpoint_IdAndActiveTrueAndDetectedAtAfter(endpointId, expectedSince)).thenReturn(5L);

        DetectionResult result = detector.evaluate(context, rule);
        assertThat(result.detected()).isTrue();
    }

    @Test
    void testCountAboveThreshold() {
        DetectionContext context = new DetectionContext("VPN", endpointId, null, occurredAt, null);
        Instant expectedSince = occurredAt.minusSeconds(60);
        when(vpnEventRepository.countByEndpoint_IdAndActiveTrueAndDetectedAtAfter(endpointId, expectedSince)).thenReturn(6L);

        DetectionResult result = detector.evaluate(context, rule);
        assertThat(result.detected()).isTrue();
    }

    /**
     * The detector delegates "active only" filtering entirely to the
     * repository method it calls - countByEndpoint_IdAndActiveTrueAndDetectedAtAfter
     * excludes inactive (active=false) rows by construction. This asserts
     * the detector trusts exactly that count: even if more VPN rows exist
     * in total (active and inactive combined), only the active-only count
     * the repository returns is compared against the threshold.
     */
    @Test
    void testInactiveEventsNotCounted() {
        DetectionContext context = new DetectionContext("VPN", endpointId, null, occurredAt, null);
        Instant expectedSince = occurredAt.minusSeconds(60);
        // Simulates 8 total VPN rows for this endpoint in the window (5
        // active, 3 inactive) - the active-only repository method returns
        // just the 5, which meets the threshold of 5.
        when(vpnEventRepository.countByEndpoint_IdAndActiveTrueAndDetectedAtAfter(endpointId, expectedSince)).thenReturn(5L);

        DetectionResult result = detector.evaluate(context, rule);
        assertThat(result.detected()).isTrue();
        verify(vpnEventRepository).countByEndpoint_IdAndActiveTrueAndDetectedAtAfter(endpointId, expectedSince);
    }

    @Test
    void testCorrectSinceTimestampPassedToRepository() {
        DetectionContext context = new DetectionContext("VPN", endpointId, null, occurredAt, null);
        Instant expectedSince = occurredAt.minusSeconds(60);
        when(vpnEventRepository.countByEndpoint_IdAndActiveTrueAndDetectedAtAfter(eq(endpointId), eq(expectedSince))).thenReturn(5L);

        detector.evaluate(context, rule);

        ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(vpnEventRepository).countByEndpoint_IdAndActiveTrueAndDetectedAtAfter(eq(endpointId), sinceCaptor.capture());
        assertThat(sinceCaptor.getValue()).isEqualTo(expectedSince);
    }

    @Test
    void testPositiveResultContainsEndpointIdAndNullUserId() {
        DetectionContext context = new DetectionContext("VPN", endpointId, UUID.randomUUID(), occurredAt, null);
        Instant expectedSince = occurredAt.minusSeconds(60);
        when(vpnEventRepository.countByEndpoint_IdAndActiveTrueAndDetectedAtAfter(endpointId, expectedSince)).thenReturn(5L);

        DetectionResult result = detector.evaluate(context, rule);

        assertThat(result.detected()).isTrue();
        assertThat(result.ruleId()).isEqualTo(ruleId);
        assertThat(result.severity()).isEqualTo(DetectionRule.Severity.HIGH);
        assertThat(result.endpointId()).isEqualTo(endpointId);
        assertThat(result.userId()).isNull();
    }
}
