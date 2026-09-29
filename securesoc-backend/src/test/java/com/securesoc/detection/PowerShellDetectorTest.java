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
class PowerShellDetectorTest {

    @Mock
    private RunningAppRepository runningAppRepository;

    private PowerShellDetector detector;

    private UUID endpointId;
    private Instant occurredAt;

    @BeforeEach
    void setUp() {
        detector = new PowerShellDetector(runningAppRepository);
        endpointId = UUID.randomUUID();
        occurredAt = Instant.now();
    }

    private DetectionRule powerShellMatchRule(String processName, String commandPattern) {
        DetectionRule rule = new DetectionRule();
        rule.setId(UUID.randomUUID());
        rule.setRuleType(DetectionRule.RuleType.POWERSHELL_MATCH);
        rule.setEventSource("RUNNING_APP");
        rule.setProcessName(processName);
        rule.setCommandPattern(commandPattern);
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
        DetectionRule rule = powerShellMatchRule("powershell.exe", "-encodedcommand");
        rule.setRuleType(DetectionRule.RuleType.PROCESS_MATCH);

        assertFalse(detector.supports(rule));
    }

    @Test
    void supports_wrongEventSource_returnsFalse() {
        DetectionRule rule = powerShellMatchRule("powershell.exe", "-encodedcommand");
        rule.setEventSource("USB_EVENT");

        assertFalse(detector.supports(rule));
    }

    @Test
    void supports_powerShellMatchAndRunningApp_returnsTrue() {
        assertTrue(detector.supports(powerShellMatchRule("powershell.exe", "-encodedcommand")));
    }

    // -----------------------------------------------------------------
    // evaluate() - defensive guards
    // -----------------------------------------------------------------

    @Test
    void evaluate_nullContext_returnsNone_andNeverQueriesRepository() {
        DetectionResult result = detector.evaluate(null, powerShellMatchRule("powershell.exe", "-encodedcommand"));

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

    @Test
    void evaluate_wrongRuleType_returnsNone_andNeverQueriesRepository() {
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);
        DetectionRule rule = powerShellMatchRule("powershell.exe", "-encodedcommand");
        rule.setRuleType(DetectionRule.RuleType.PROCESS_MATCH);

        DetectionResult result = detector.evaluate(context, rule);

        assertFalse(result.detected());
        verifyNoInteractions(runningAppRepository);
    }

    @Test
    void evaluate_wrongEventSource_returnsNone_andNeverQueriesRepository() {
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);
        DetectionRule rule = powerShellMatchRule("powershell.exe", "-encodedcommand");
        rule.setEventSource("USB_EVENT");

        DetectionResult result = detector.evaluate(context, rule);

        assertFalse(result.detected());
        verifyNoInteractions(runningAppRepository);
    }

    @Test
    void evaluate_missingEndpointId_returnsNone_andNeverQueriesRepository() {
        DetectionContext context = new DetectionContext("RUNNING_APP", null, null, occurredAt, null);

        DetectionResult result = detector.evaluate(context, powerShellMatchRule("powershell.exe", "-encodedcommand"));

        assertFalse(result.detected());
        verifyNoInteractions(runningAppRepository);
    }

    @Test
    void evaluate_missingOccurredAt_returnsNone_andNeverQueriesRepository() {
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, null, null);

        DetectionResult result = detector.evaluate(context, powerShellMatchRule("powershell.exe", "-encodedcommand"));

        assertFalse(result.detected());
        verifyNoInteractions(runningAppRepository);
    }

    @Test
    void evaluate_nullProcessName_returnsNone_andNeverQueriesRepository() {
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);

        DetectionResult result = detector.evaluate(context, powerShellMatchRule(null, "-encodedcommand"));

        assertFalse(result.detected());
        verifyNoInteractions(runningAppRepository);
    }

    @Test
    void evaluate_blankProcessName_returnsNone_andNeverQueriesRepository() {
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);

        DetectionResult result = detector.evaluate(context, powerShellMatchRule("   ", "-encodedcommand"));

        assertFalse(result.detected());
        verifyNoInteractions(runningAppRepository);
    }

    @Test
    void evaluate_nullCommandPattern_returnsNone_andNeverQueriesRepository() {
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);

        DetectionResult result = detector.evaluate(context, powerShellMatchRule("powershell.exe", null));

        assertFalse(result.detected());
        verifyNoInteractions(runningAppRepository);
    }

    @Test
    void evaluate_blankCommandPattern_returnsNone_andNeverQueriesRepository() {
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);

        DetectionResult result = detector.evaluate(context, powerShellMatchRule("powershell.exe", "   "));

        assertFalse(result.detected());
        verifyNoInteractions(runningAppRepository);
    }

    // -----------------------------------------------------------------
    // evaluate() - match outcomes
    // -----------------------------------------------------------------

    @Test
    void evaluate_noRepositoryMatch_returnsNone() {
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);
        when(runningAppRepository
            .existsBySnapshot_Endpoint_IdAndProcessNameIgnoreCaseAndCommandLineContainingIgnoreCaseAndSnapshot_CapturedAtAfter(
                any(), anyString(), anyString(), any())).thenReturn(false);

        DetectionResult result = detector.evaluate(context, powerShellMatchRule("powershell.exe", "-encodedcommand"));

        assertFalse(result.detected());
    }

    @Test
    void evaluate_repositoryMatch_returnsDetectedWithCorrectFields() {
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);
        DetectionRule rule = powerShellMatchRule("powershell.exe", "-encodedcommand");
        when(runningAppRepository
            .existsBySnapshot_Endpoint_IdAndProcessNameIgnoreCaseAndCommandLineContainingIgnoreCaseAndSnapshot_CapturedAtAfter(
                any(), anyString(), anyString(), any())).thenReturn(true);

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
    void evaluate_caseInsensitiveProcessName_stillDetects() {
        // The repository method itself performs the case-insensitive
        // comparison (IgnoreCase); this test only proves the detector
        // passes the rule's processName through unmodified rather than
        // normalizing case itself, and trusts a true repository result -
        // mirrors SuspiciousProcessDetectorTest's equivalent test.
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);
        DetectionRule rule = powerShellMatchRule("PowerShell.EXE", "-encodedcommand");
        when(runningAppRepository
            .existsBySnapshot_Endpoint_IdAndProcessNameIgnoreCaseAndCommandLineContainingIgnoreCaseAndSnapshot_CapturedAtAfter(
                eq(endpointId), eq("PowerShell.EXE"), eq("-encodedcommand"), any())).thenReturn(true);

        DetectionResult result = detector.evaluate(context, rule);

        assertTrue(result.detected());
    }

    @Test
    void evaluate_caseInsensitiveCommandPattern_stillDetects() {
        // Same idea as above, for the commandPattern argument: the
        // repository's ContainingIgnoreCase comparison does the
        // case-folding, so this only proves the detector passes the
        // rule's commandPattern through unmodified.
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);
        DetectionRule rule = powerShellMatchRule("powershell.exe", "-EncodedCommand");
        when(runningAppRepository
            .existsBySnapshot_Endpoint_IdAndProcessNameIgnoreCaseAndCommandLineContainingIgnoreCaseAndSnapshot_CapturedAtAfter(
                eq(endpointId), eq("powershell.exe"), eq("-EncodedCommand"), any())).thenReturn(true);

        DetectionResult result = detector.evaluate(context, rule);

        assertTrue(result.detected());
    }

    @Test
    void evaluate_nearMissCommand_repositoryReportsNoMatch_returnsNone() {
        // Substring-vs-no-substring is enforced by the repository's
        // ContainingIgnoreCase comparison, not by any string logic in the
        // detector - this test proves the detector correctly reports "no
        // detection" when the repository (standing in for that substring
        // guarantee) returns false for a command line that does not
        // contain the configured pattern.
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);
        when(runningAppRepository
            .existsBySnapshot_Endpoint_IdAndProcessNameIgnoreCaseAndCommandLineContainingIgnoreCaseAndSnapshot_CapturedAtAfter(
                any(), anyString(), anyString(), any())).thenReturn(false);

        DetectionResult result = detector.evaluate(context, powerShellMatchRule("powershell.exe", "-encodedcommand"));

        assertFalse(result.detected());
    }

    @Test
    void evaluate_nearMissProcessName_repositoryReportsNoMatch_returnsNone() {
        // Exact-match behavior for processName (e.g. "powershell-helper.exe"
        // must NOT match "powershell.exe") is enforced by the repository's
        // exact IgnoreCase comparison, not by any substring logic in the
        // detector - mirrors SuspiciousProcessDetectorTest's equivalent
        // test.
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);
        when(runningAppRepository
            .existsBySnapshot_Endpoint_IdAndProcessNameIgnoreCaseAndCommandLineContainingIgnoreCaseAndSnapshot_CapturedAtAfter(
                any(), anyString(), anyString(), any())).thenReturn(false);

        DetectionResult result = detector.evaluate(context, powerShellMatchRule("powershell.exe", "-encodedcommand"));

        assertFalse(result.detected());
    }

    @Test
    void evaluate_correctRepositoryArgumentsPassed() {
        DetectionContext context = new DetectionContext("RUNNING_APP", endpointId, null, occurredAt, null);
        DetectionRule rule = powerShellMatchRule("powershell.exe", "-encodedcommand");
        when(runningAppRepository
            .existsBySnapshot_Endpoint_IdAndProcessNameIgnoreCaseAndCommandLineContainingIgnoreCaseAndSnapshot_CapturedAtAfter(
                any(), anyString(), anyString(), any())).thenReturn(false);

        detector.evaluate(context, rule);

        ArgumentCaptor<UUID> endpointCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> processNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> commandPatternCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(runningAppRepository)
            .existsBySnapshot_Endpoint_IdAndProcessNameIgnoreCaseAndCommandLineContainingIgnoreCaseAndSnapshot_CapturedAtAfter(
                endpointCaptor.capture(), processNameCaptor.capture(), commandPatternCaptor.capture(), sinceCaptor.capture());

        assertEquals(endpointId, endpointCaptor.getValue());
        assertEquals("powershell.exe", processNameCaptor.getValue());
        assertEquals("-encodedcommand", commandPatternCaptor.getValue());
        assertTrue(sinceCaptor.getValue().isBefore(occurredAt),
            "since must be strictly before occurredAt so the triggering snapshot's row is included");
    }
}
