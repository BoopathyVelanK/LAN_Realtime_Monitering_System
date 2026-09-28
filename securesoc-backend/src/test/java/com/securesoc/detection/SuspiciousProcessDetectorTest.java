package com.securesoc.detection;

import com.securesoc.entity.DetectionRule;
import com.securesoc.repository.RunningAppRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuspiciousProcessDetectorTest {

    @Mock
    private RunningAppRepository runningAppRepository;

    private SuspiciousProcessDetector detector;

    private UUID endpointId;
    private Instant occurredAt;

    @BeforeEach
    void setUp() {
        detector = new SuspiciousProcessDetector(runningAppRepository);
        endpointId = UUID.randomUUID();
        occurredAt = Instant.now();
    }

    private DetectionRule processMatchRule(String processName) {
        DetectionRule rule = new DetectionRule();
        rule.setId(UUID.randomUUID());
        rule.setRuleType(DetectionRule.RuleType.PROCESS_MATCH);
        rule.setEventSource("RUNNING_APP");
        rule.setProcessName(processName);
        rule.setSeverity(DetectionRule.Severity.HIGH);
        return rule;
    }

    // -----------------------------------------------------------------
    // supports()
    // -----------------------------------------------------------------

    @Test
    void supports_nullRule_returnsFalse() {
        assertFalse(detector.supports(null));
    }

    @Test
    void supports_wrongRuleType_returnsFalse() {
        DetectionRule rule = processMatchRule("powershell.exe");
        rule.setRuleType(DetectionRule.RuleType.THRESHOLD);

        assertFalse(detector.supports(rule));
    }

    @Test
    void supports_wrongEventSource_returnsFalse() {
        DetectionRule rule = processMatchRule("powershell.exe");
        rule.setEventSource("USB_EVENT");

        assertFalse(detector.supports(rule));
    }

    @Test
    void supports_processMatchAndRunningApp_returnsTrue() {
        assertTrue(detector.supports(processMatchRule("powershell.exe")));
    }

    // -----------------------------------------------------------------
    // evaluate() - defensive guards
    // -----------------------------------------------------------------

    @Test
    void evaluate_nullContext_returnsNone_andNeverQueriesRepository() {
        DetectionResult result = detector.evaluate(null, processMatchRule("powershell.exe"));

        assertFalse(result.detected());
        verifyNoInteractions(runningAppRepository);
    }

    @Test
    void evaluate_nullRule_returnsNone_andNeverQueriesRepository() {
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);

        DetectionResult result = detector.evaluate(context, null);

        assertFalse(result.detected());
        verifyNoInteractions(runningAppRepository);
    }

    // Note: there is deliberately no evaluate()-level test asserting that a
    // context.eventSource()/rule.eventSource() mismatch is rejected before
    // querying the repository. Neither this detector nor the established
    // VpnEventDetector/UsbEventDetector validate context.eventSource()
    // inside evaluate() - DetectionEngine.evaluate() already selects rules
    // via findByEventSourceAndEnabledTrue(context.eventSource()) before any
    // detector is ever invoked, so a detector is never handed a mismatched
    // (context, rule) pair in real operation. The relevant coverage for a
    // wrong event source is supports_wrongEventSource_returnsFalse() above,
    // which tests the actual contract (the rule's own eventSource).

    @Test
    void evaluate_wrongRuleType_returnsNone_andNeverQueriesRepository() {
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);
        DetectionRule rule = processMatchRule("powershell.exe");
        rule.setRuleType(DetectionRule.RuleType.THRESHOLD);

        DetectionResult result = detector.evaluate(context, rule);

        assertFalse(result.detected());
        verifyNoInteractions(runningAppRepository);
    }

    @Test
    void evaluate_missingEndpointId_returnsNone_andNeverQueriesRepository() {
        DetectionContext context = new DetectionContext("RUNNING_APP", null, null, occurredAt, null);

        DetectionResult result = detector.evaluate(context, processMatchRule("powershell.exe"));

        assertFalse(result.detected());
        verifyNoInteractions(runningAppRepository);
    }

    @Test
    void evaluate_missingOccurredAt_returnsNone_andNeverQueriesRepository() {
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, null, null);

        DetectionResult result = detector.evaluate(context, processMatchRule("powershell.exe"));

        assertFalse(result.detected());
        verifyNoInteractions(runningAppRepository);
    }

    @Test
    void evaluate_nullProcessName_returnsNone_andNeverQueriesRepository() {
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);

        DetectionResult result = detector.evaluate(context, processMatchRule(null));

        assertFalse(result.detected());
        verifyNoInteractions(runningAppRepository);
    }

    @Test
    void evaluate_blankProcessName_returnsNone_andNeverQueriesRepository() {
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);

        DetectionResult result = detector.evaluate(context, processMatchRule("   "));

        assertFalse(result.detected());
        verifyNoInteractions(runningAppRepository);
    }

    // -----------------------------------------------------------------
    // evaluate() - match outcomes
    // -----------------------------------------------------------------

    @Test
    void evaluate_noMatchingProcess_returnsNone() {
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);
        when(runningAppRepository.existsBySnapshot_Endpoint_IdAndProcessNameIgnoreCaseAndSnapshot_CapturedAtAfter(
            any(), anyString(), any())).thenReturn(false);

        DetectionResult result = detector.evaluate(context, processMatchRule("powershell.exe"));

        assertFalse(result.detected());
    }

    @Test
    void evaluate_matchingProcess_returnsDetectedWithCorrectFields() {
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);
        DetectionRule rule = processMatchRule("powershell.exe");
        when(runningAppRepository.existsBySnapshot_Endpoint_IdAndProcessNameIgnoreCaseAndSnapshot_CapturedAtAfter(
            any(), anyString(), any())).thenReturn(true);

        DetectionResult result = detector.evaluate(context, rule);

        assertTrue(result.detected());
        assertEquals(rule.getId(), result.ruleId());
        assertEquals(DetectionRule.Severity.HIGH, result.severity());
        assertEquals(endpointId, result.endpointId());
        assertNull(result.userId(), "userId must be null for endpoint telemetry");
        assertNotNull(result.title());
        assertNotNull(result.description());
    }

    @Test
    void evaluate_caseInsensitiveMatch_stillDetects() {
        // The repository method itself performs the case-insensitive
        // comparison (IgnoreCase); this test only proves the detector
        // passes the rule's processName through unmodified rather than
        // normalizing case itself, and trusts a true repository result
        // exactly like the false case above.
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);
        DetectionRule rule = processMatchRule("PowerShell.EXE");
        when(runningAppRepository.existsBySnapshot_Endpoint_IdAndProcessNameIgnoreCaseAndSnapshot_CapturedAtAfter(
            eq(endpointId), eq("PowerShell.EXE"), any())).thenReturn(true);

        DetectionResult result = detector.evaluate(context, rule);

        assertTrue(result.detected());
    }

    @Test
    void evaluate_similarButNotExactProcessName_detectorStillDelegatesToRepository_noMatchReturnsNone() {
        // Exact-match behavior (e.g. "powershell_ise.exe" or
        // "powershell.exe.tmp" must NOT match "powershell.exe") is
        // enforced by the repository's exact IgnoreCase comparison, not
        // by any substring logic in the detector - this test proves the
        // detector correctly reports "no detection" when the repository
        // (standing in for that exact-match guarantee) returns false.
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);
        when(runningAppRepository.existsBySnapshot_Endpoint_IdAndProcessNameIgnoreCaseAndSnapshot_CapturedAtAfter(
            any(), anyString(), any())).thenReturn(false);

        DetectionResult result = detector.evaluate(context, processMatchRule("powershell.exe"));

        assertFalse(result.detected());
    }

    @Test
    void evaluate_correctRepositoryArgumentsPassed() {
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);
        DetectionRule rule = processMatchRule("powershell.exe");
        when(runningAppRepository.existsBySnapshot_Endpoint_IdAndProcessNameIgnoreCaseAndSnapshot_CapturedAtAfter(
            any(), anyString(), any())).thenReturn(false);

        detector.evaluate(context, rule);

        ArgumentCaptor<UUID> endpointCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> processNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(runningAppRepository).existsBySnapshot_Endpoint_IdAndProcessNameIgnoreCaseAndSnapshot_CapturedAtAfter(
            endpointCaptor.capture(), processNameCaptor.capture(), sinceCaptor.capture());

        assertEquals(endpointId, endpointCaptor.getValue());
        assertEquals("powershell.exe", processNameCaptor.getValue());
        assertTrue(sinceCaptor.getValue().isBefore(occurredAt),
            "since must be strictly before occurredAt so the triggering snapshot's row is included");
    }
}
