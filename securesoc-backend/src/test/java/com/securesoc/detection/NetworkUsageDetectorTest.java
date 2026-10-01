package com.securesoc.detection;

import com.securesoc.entity.DetectionRule;
import com.securesoc.repository.NetworkUsageEventRepository;
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
class NetworkUsageDetectorTest {

    private static final long MIB = 1024L * 1024L;

    @Mock
    private NetworkUsageEventRepository networkUsageEventRepository;

    @InjectMocks
    private NetworkUsageDetector detector;

    private DetectionRule rule;
    private UUID endpointId;
    private UUID ruleId;
    private Instant occurredAt;
    private Instant expectedSince;

    @BeforeEach
    void setUp() {
        ruleId = UUID.randomUUID();
        rule = new DetectionRule();
        rule.setId(ruleId);
        rule.setRuleType(DetectionRule.RuleType.THRESHOLD);
        rule.setEventSource("NETWORK_USAGE");
        rule.setThreshold(100); // MiB
        rule.setWindowSeconds(300);
        rule.setSeverity(DetectionRule.Severity.MEDIUM);

        endpointId = UUID.randomUUID();
        occurredAt = Instant.parse("2026-09-30T10:00:00Z");
        expectedSince = occurredAt.minusSeconds(300);
    }

    private DetectionContext context() {
        return new DetectionContext("NETWORK_USAGE", endpointId, null, occurredAt, null);
    }

    private void stubTotal(long bytes) {
        when(networkUsageEventRepository.sumTotalBytesInWindow(endpointId, expectedSince, occurredAt))
            .thenReturn(bytes);
    }

    // ---- supports() ------------------------------------------------------

    @Test
    void supports_thresholdWithNetworkUsageSource_true() {
        assertThat(detector.supports(rule)).isTrue();
    }

    @Test
    void supports_wrongEventSource_false() {
        rule.setEventSource("USB_EVENT");
        assertThat(detector.supports(rule)).isFalse();
    }

    @Test
    void supports_wrongRuleType_false() {
        rule.setRuleType(DetectionRule.RuleType.PROCESS_MATCH);
        assertThat(detector.supports(rule)).isFalse();
    }

    @Test
    void supports_nullRule_false() {
        assertThat(detector.supports(null)).isFalse();
    }

    // ---- defensive validation -------------------------------------------

    @Test
    void testNullContext() {
        assertThat(detector.evaluate(null, rule).detected()).isFalse();
        verifyNoInteractions(networkUsageEventRepository);
    }

    @Test
    void testNullRule() {
        assertThat(detector.evaluate(context(), null).detected()).isFalse();
        verifyNoInteractions(networkUsageEventRepository);
    }

    @Test
    void testUnsupportedRuleReturnsNoDetection() {
        rule.setEventSource("OTHER_EVENT");
        assertThat(detector.evaluate(context(), rule).detected()).isFalse();
        verifyNoInteractions(networkUsageEventRepository);
    }

    @Test
    void testMissingEndpointId() {
        DetectionContext ctx = new DetectionContext("NETWORK_USAGE", null, null, occurredAt, null);
        assertThat(detector.evaluate(ctx, rule).detected()).isFalse();
        verifyNoInteractions(networkUsageEventRepository);
    }

    @Test
    void testMissingOccurredAt() {
        DetectionContext ctx = new DetectionContext("NETWORK_USAGE", endpointId, null, null, null);
        assertThat(detector.evaluate(ctx, rule).detected()).isFalse();
        verifyNoInteractions(networkUsageEventRepository);
    }

    @Test
    void testNullThreshold() {
        rule.setThreshold(null);
        assertThat(detector.evaluate(context(), rule).detected()).isFalse();
        verifyNoInteractions(networkUsageEventRepository);
    }

    @Test
    void testNonPositiveThreshold() {
        rule.setThreshold(0);
        assertThat(detector.evaluate(context(), rule).detected()).isFalse();
        rule.setThreshold(-5);
        assertThat(detector.evaluate(context(), rule).detected()).isFalse();
        verifyNoInteractions(networkUsageEventRepository);
    }

    @Test
    void testNullWindowSeconds() {
        rule.setWindowSeconds(null);
        assertThat(detector.evaluate(context(), rule).detected()).isFalse();
        verifyNoInteractions(networkUsageEventRepository);
    }

    @Test
    void testNonPositiveWindowSeconds() {
        rule.setWindowSeconds(0);
        assertThat(detector.evaluate(context(), rule).detected()).isFalse();
        rule.setWindowSeconds(-1);
        assertThat(detector.evaluate(context(), rule).detected()).isFalse();
        verifyNoInteractions(networkUsageEventRepository);
    }

    // ---- detection behaviour --------------------------------------------

    @Test
    void testNoMatchingUsage_notDetected() {
        stubTotal(0L);
        assertThat(detector.evaluate(context(), rule).detected()).isFalse();
    }

    @Test
    void testBelowThreshold_notDetected() {
        stubTotal(100 * MIB - 1);
        assertThat(detector.evaluate(context(), rule).detected()).isFalse();
    }

    @Test
    void testExactlyThreshold_detected() {
        stubTotal(100 * MIB);
        assertThat(detector.evaluate(context(), rule).detected()).isTrue();
    }

    @Test
    void testAboveThreshold_detected() {
        stubTotal(100 * MIB + 1);
        assertThat(detector.evaluate(context(), rule).detected()).isTrue();
    }

    @Test
    void testLargeThresholdDoesNotOverflow() {
        // Integer.MAX_VALUE MiB is ~2 PiB - far beyond int range in bytes.
        rule.setThreshold(Integer.MAX_VALUE);
        stubTotal(Integer.MAX_VALUE * MIB - 1);
        assertThat(detector.evaluate(context(), rule).detected()).isFalse();

        stubTotal(Integer.MAX_VALUE * MIB);
        assertThat(detector.evaluate(context(), rule).detected()).isTrue();
    }

    @Test
    void testNegativeTotalIsTreatedAsZero() {
        // The repository query clamps negative addends; this is the
        // detector's own safety net should a negative total ever surface.
        stubTotal(-500 * MIB);
        assertThat(detector.evaluate(context(), rule).detected()).isFalse();
    }

    @Test
    void testRepositoryReceivesEndpointAndExactWindowBoundaries() {
        stubTotal(100 * MIB);

        detector.evaluate(context(), rule);

        ArgumentCaptor<UUID> endpointCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> untilCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(networkUsageEventRepository)
            .sumTotalBytesInWindow(endpointCaptor.capture(), sinceCaptor.capture(), untilCaptor.capture());

        assertThat(endpointCaptor.getValue()).isEqualTo(endpointId);
        assertThat(sinceCaptor.getValue()).isEqualTo(occurredAt.minusSeconds(300));
        // Upper bound is the context timestamp itself, not "now".
        assertThat(untilCaptor.getValue()).isEqualTo(occurredAt);
    }

    @Test
    void testWindowFollowsRuleWindowSeconds() {
        rule.setWindowSeconds(60);
        when(networkUsageEventRepository.sumTotalBytesInWindow(
            eq(endpointId), eq(occurredAt.minusSeconds(60)), eq(occurredAt))).thenReturn(0L);

        assertThat(detector.evaluate(context(), rule).detected()).isFalse();
        verify(networkUsageEventRepository).sumTotalBytesInWindow(
            endpointId, occurredAt.minusSeconds(60), occurredAt);
    }

    // ---- result contents ------------------------------------------------

    @Test
    void testPositiveResultContents() {
        stubTotal(150 * MIB);
        // A userId on the context must never leak into an endpoint result.
        DetectionContext ctx = new DetectionContext("NETWORK_USAGE", endpointId, UUID.randomUUID(), occurredAt, null);

        DetectionResult result = detector.evaluate(ctx, rule);

        assertThat(result.detected()).isTrue();
        assertThat(result.ruleId()).isEqualTo(ruleId);
        assertThat(result.severity()).isEqualTo(DetectionRule.Severity.MEDIUM);
        assertThat(result.endpointId()).isEqualTo(endpointId);
        assertThat(result.userId()).isNull();
        assertThat(result.title()).isEqualTo("Excessive network usage detected");
        assertThat(result.description())
            .contains(String.valueOf(150 * MIB))
            .contains("150 MiB")
            .contains(endpointId.toString())
            .contains("300 seconds")
            .contains("threshold: 100 MiB");
    }

    @Test
    void testSeverityComesFromRule() {
        rule.setSeverity(DetectionRule.Severity.CRITICAL);
        stubTotal(100 * MIB);
        assertThat(detector.evaluate(context(), rule).severity()).isEqualTo(DetectionRule.Severity.CRITICAL);
    }
}
