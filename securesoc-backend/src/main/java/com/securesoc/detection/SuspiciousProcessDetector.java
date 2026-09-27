package com.securesoc.detection;

import com.securesoc.entity.DetectionRule;
import com.securesoc.repository.RunningAppRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Detects a specific, exact (case-insensitive) process name running on an
 * endpoint, per a {@code PROCESS_MATCH} {@code DetectionRule.processName}
 * value - e.g. flagging {@code "powershell.exe"} as suspicious.
 *
 * Unlike {@link RepeatedFailedLoginDetector}/{@link UsbEventDetector}/
 * {@link VpnEventDetector}, this detector has no rule-configured
 * threshold/windowSeconds - a single matching process is itself the
 * signal, not a count crossing a threshold. See
 * {@link #MATCH_WINDOW_SECONDS} for why a small fixed time boundary is
 * still needed to scope the repository query to the triggering snapshot.
 *
 * Per the locked design, this detector does not read
 * {@code DetectionContext.event()} - {@code MonitoringService.recordRunningApps()}
 * always passes {@code null} there, and {@code RunningApp}/
 * {@code RunningAppSnapshot} deliberately do not implement
 * {@link DetectionEvent}. All data needed comes from the
 * endpointId/occurredAt context fields plus a direct repository query,
 * exactly like {@link VpnEventDetector}.
 */
@Component
public class SuspiciousProcessDetector implements Detector {

    private static final String EVENT_SOURCE = "RUNNING_APP";

    /**
     * {@code DetectionContext.occurredAt()} for running-app telemetry is
     * always the just-persisted {@code RunningAppSnapshot.capturedAt}
     * value, taken from the same in-memory {@link Instant} used to INSERT
     * that row (see {@code MonitoringService.recordRunningApps()}).
     * PostgreSQL's {@code timestamptz} column stores microsecond
     * precision, while a Java {@code Instant} can carry nanosecond
     * precision, so the value bound into the INSERT can be silently
     * truncated on write. Comparing the stored (possibly truncated-down)
     * value against the original, untruncated {@code Instant} with a
     * strict {@code >} (or {@code >=}) condition risks missing the very
     * row this detector is meant to see. A small fixed backward margin -
     * not a rule-configurable detection window - makes the repository's
     * "After" comparison robust to that truncation while staying tightly
     * scoped to the triggering snapshot: the agent's monitoring cycle
     * interval is tens of seconds at minimum (see
     * securesoc-agent/config.ini's {@code monitoring_interval_seconds}),
     * so this margin cannot accidentally pull in an earlier, unrelated
     * snapshot.
     */
    private static final long MATCH_WINDOW_SECONDS = 2L;

    private final RunningAppRepository runningAppRepository;

    public SuspiciousProcessDetector(RunningAppRepository runningAppRepository) {
        this.runningAppRepository = runningAppRepository;
    }

    @Override
    public boolean supports(DetectionRule rule) {
        return rule != null
            && rule.getRuleType() == DetectionRule.RuleType.PROCESS_MATCH
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

        Instant since = context.occurredAt().minusSeconds(MATCH_WINDOW_SECONDS);
        boolean matched = runningAppRepository
            .existsBySnapshot_Endpoint_IdAndProcessNameIgnoreCaseAndSnapshot_CapturedAtAfter(
                context.endpointId(), processName, since);

        if (!matched) {
            return DetectionResult.none();
        }

        String title = "Suspicious process detected";
        String description = "Process '%s' was observed running on endpoint %s."
            .formatted(processName, context.endpointId());

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
