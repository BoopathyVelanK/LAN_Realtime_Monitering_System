package com.securesoc.detection;

import com.securesoc.entity.DetectionRule;
import com.securesoc.repository.UsbEventRepository;
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
class UsbEventDetectorTest {

    @Mock
    private UsbEventRepository usbEventRepository;

    @InjectMocks
    private UsbEventDetector detector;

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
        rule.setEventSource("USB_EVENT");
        rule.setThreshold(5);
        rule.setWindowSeconds(60);
        rule.setSeverity(DetectionRule.Severity.MEDIUM);

        endpointId = UUID.randomUUID();
        occurredAt = Instant.parse("2026-08-30T10:00:00Z");
    }

    @Test
    void testMissingEndpointId() {
        DetectionContext context = new DetectionContext("USB", null, null, occurredAt, null);
        DetectionResult result = detector.evaluate(context, rule);
        assertThat(result.detected()).isFalse();
        verifyNoInteractions(usbEventRepository);
    }

    @Test
    void testMissingOccurredAt() {
        DetectionContext context = new DetectionContext("USB", endpointId, null, null, null);
        DetectionResult result = detector.evaluate(context, rule);
        assertThat(result.detected()).isFalse();
        verifyNoInteractions(usbEventRepository);
    }

    @Test
    void testCountBelowThreshold() {
        DetectionContext context = new DetectionContext("USB", endpointId, null, occurredAt, null);
        Instant expectedSince = occurredAt.minusSeconds(60);
        when(usbEventRepository.countByEndpoint_IdAndEventTimeAfter(endpointId, expectedSince)).thenReturn(4L);

        DetectionResult result = detector.evaluate(context, rule);
        assertThat(result.detected()).isFalse();
    }

    @Test
    void testCountEqualToThreshold() {
        DetectionContext context = new DetectionContext("USB", endpointId, null, occurredAt, null);
        Instant expectedSince = occurredAt.minusSeconds(60);
        when(usbEventRepository.countByEndpoint_IdAndEventTimeAfter(endpointId, expectedSince)).thenReturn(5L);

        DetectionResult result = detector.evaluate(context, rule);
        assertThat(result.detected()).isTrue();
    }

    @Test
    void testCountAboveThreshold() {
        DetectionContext context = new DetectionContext("USB", endpointId, null, occurredAt, null);
        Instant expectedSince = occurredAt.minusSeconds(60);
        when(usbEventRepository.countByEndpoint_IdAndEventTimeAfter(endpointId, expectedSince)).thenReturn(6L);

        DetectionResult result = detector.evaluate(context, rule);
        assertThat(result.detected()).isTrue();
    }

    @Test
    void testCorrectSinceTimestampPassedToRepository() {
        DetectionContext context = new DetectionContext("USB", endpointId, null, occurredAt, null);
        Instant expectedSince = occurredAt.minusSeconds(60);
        when(usbEventRepository.countByEndpoint_IdAndEventTimeAfter(eq(endpointId), eq(expectedSince))).thenReturn(5L);

        detector.evaluate(context, rule);

        ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(usbEventRepository).countByEndpoint_IdAndEventTimeAfter(eq(endpointId), sinceCaptor.capture());
        assertThat(sinceCaptor.getValue()).isEqualTo(expectedSince);
    }

    @Test
    void testPositiveResultContainsEndpointIdAndNullUserId() {
        DetectionContext context = new DetectionContext("USB", endpointId, UUID.randomUUID(), occurredAt, null);
        Instant expectedSince = occurredAt.minusSeconds(60);
        when(usbEventRepository.countByEndpoint_IdAndEventTimeAfter(endpointId, expectedSince)).thenReturn(5L);

        DetectionResult result = detector.evaluate(context, rule);
        
        assertThat(result.detected()).isTrue();
        assertThat(result.ruleId()).isEqualTo(ruleId);
        assertThat(result.severity()).isEqualTo(DetectionRule.Severity.MEDIUM);
        assertThat(result.endpointId()).isEqualTo(endpointId);
        assertThat(result.userId()).isNull();
    }

    @Test
    void testUnsupportedRuleOrEventSourceReturnsNoDetection() {
        DetectionRule unsupportedRule = new DetectionRule();
        unsupportedRule.setRuleType(DetectionRule.RuleType.THRESHOLD);
        unsupportedRule.setEventSource("OTHER_EVENT");

        DetectionContext context = new DetectionContext("USB", endpointId, null, occurredAt, null);
        
        DetectionResult result = detector.evaluate(context, unsupportedRule);
        assertThat(result.detected()).isFalse();
        verifyNoInteractions(usbEventRepository);
    }
}
