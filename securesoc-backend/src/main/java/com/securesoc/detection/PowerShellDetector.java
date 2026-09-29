package com.securesoc.detection;

import com.securesoc.entity.DetectionRule;
import com.securesoc.repository.RunningAppRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Detects a configured PowerShell invocation pattern on an endpoint, per a
 * {@code POWERSHELL_MATCH} {@code DetectionRule}: an exact (case-insensitive)
 * {@code processName} (e.g. {@code "powershell.exe"} or {@code "pwsh.exe"})
 * combined with a case-insensitive substring match against that process's
 * {@code commandLine} (e.g. {@code "-encodedcommand"}, {@code "downloadstring"}).
 *
 * This is a distinct {@code ruleType} from {@link SuspiciousProcessDetector}'s
 * {@code PROCESS_MATCH} - rather than reusing it - specifically so the two
 * detectors never both {@code supports()} the same rule; see
 * {@code DetectionEngine.evaluate()}'s {@code AmbiguousDetectorException}.
 * {@code PROCESS_MATCH + RUNNING_APP} continues to be handled exclusively by
 * {@link SuspiciousProcessDetector}, unchanged.
 *
 * Matching is a plain case-insensitive substring test performed by the
 * repository (see {@link RunningAppRepository}), not a regex and not a
 * shell-aware tokenizer - the command line is telemetry to compare against,
 * never interpreted or executed. The raw, telemetry-captured command line is
 * also never included in this detector's own output (title/description);
 * only the rule's own configured {@code processName}/{@code commandPattern}
 * are, since a captured command line can carry secrets/tokens/credentials
 * (see collector.py's get_running_applications docstring).
 *
 * Per the locked design, this detector does not read
 * {@code DetectionContext.event()} - {@code MonitoringService.recordRunningApps()}
 * always passes {@code null} there, exactly as for {@link SuspiciousProcessDetector}.
 */
@Component
public class PowerShellDetector implements Detector {

    private static final String EVENT_SOURCE = "RUNNING_APP";

    /** Same fixed backward margin as {@link SuspiciousProcessDetector}, and
     * for the identical reason - see that class's Javadoc on its own
     * {@code MATCH_WINDOW_SECONDS} for the full timestamp-truncation
     * rationale, which applies here unchanged. */
    private static final long MATCH_WINDOW_SECONDS = 2L;

    private final RunningAppRepository runningAppRepository;

    public PowerShellDetector(RunningAppRepository runningAppRepository) {
        this.runningAppRepository = runningAppRepository;
    }

    @Override
    public boolean supports(DetectionRule rule) {
        return rule != null
            && rule.getRuleType() == DetectionRule.RuleType.POWERSHELL_MATCH
            && EVENT_SOURCE.equals(rule.getEventSource());
    }

    @Override
    public DetectionResult evaluate(DetectionContext context, DetectionRule rule) {
        if (context == null) {
            return DetectionResult.none();
        }

        if (!supports(rule)) {
            return DetectionResult.none();
        }

        if (context.endpointId() == null) {
            return DetectionResult.none();
        }

        if (context.occurredAt() == null) {
            return DetectionResult.none();
        }

        String processName = rule.getProcessName();
        if (processName == null || processName.isBlank()) {
            return DetectionResult.none();
        }

        String commandPattern = rule.getCommandPattern();
        if (commandPattern == null || commandPattern.isBlank()) {
            return DetectionResult.none();
        }

        Instant since = context.occurredAt().minusSeconds(MATCH_WINDOW_SECONDS);
        boolean matched = runningAppRepository
            .existsBySnapshot_Endpoint_IdAndProcessNameIgnoreCaseAndCommandLineContainingIgnoreCaseAndSnapshot_CapturedAtAfter(
                context.endpointId(), processName, commandPattern, since);

        if (!matched) {
            return DetectionResult.none();
        }

        String title = "Suspicious PowerShell activity detected";
        String description = "Process '%s' matching command pattern '%s' was observed running on endpoint %s."
            .formatted(processName, commandPattern, context.endpointId());

        return new DetectionResult(
            true,
            rule.getId(),
            rule.getSeverity(),
            title,
            description,
            null, // userId must be null for endpoint telemetry
            context.endpointId()
        );
    }
}
