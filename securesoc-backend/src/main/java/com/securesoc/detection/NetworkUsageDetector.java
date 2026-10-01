package com.securesoc.detection;

import com.securesoc.entity.DetectionRule;
import com.securesoc.repository.NetworkUsageEventRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Detects excessive total network traffic for one endpoint within a rolling
 * window: THRESHOLD rules on the NETWORK_USAGE event source.
 *
 * Signal: sum of bytesSent + bytesReceived over NetworkUsageEvent rows whose
 * effective timestamp (COALESCE(sampledAt, recordedAt)) falls in
 * (occurredAt - windowSeconds, occurredAt].
 *
 * Threshold unit: rule.threshold is MiB (1 MiB = 1048576 bytes), compared as
 * totalBytes >= threshold * 1048576 using long arithmetic.
 *
 * Negative telemetry: network_usage_events has no CHECK constraint, so a
 * negative bytes value can be stored. The repository query clamps each
 * addend at zero, so a negative sample never reduces the total; this
 * detector additionally treats any negative total as zero.
 *
 * InternetUsageEvent is deliberately NOT used: it is computed from the same
 * byte deltas as NetworkUsageEvent, so summing both would double-count.
 *
 * The detector only reports whether the threshold was crossed. Alerts, risk
 * and WebSocket publishing stay in DetectionEngine's downstream services.
 */
@Component
public class NetworkUsageDetector implements Detector {

    private static final String EVENT_SOURCE = "NETWORK_USAGE";
    private static final long BYTES_PER_MIB = 1024L * 1024L;

    private final NetworkUsageEventRepository networkUsageEventRepository;

    public NetworkUsageDetector(NetworkUsageEventRepository networkUsageEventRepository) {
        this.networkUsageEventRepository = networkUsageEventRepository;
    }

    @Override
    public boolean supports(DetectionRule rule) {
        return rule != null
            && rule.getRuleType() == DetectionRule.RuleType.THRESHOLD
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

        Integer thresholdMib = rule.getThreshold();
        if (thresholdMib == null || thresholdMib <= 0) {
            return DetectionResult.none();
        }

        Integer windowSeconds = rule.getWindowSeconds();
        if (windowSeconds == null || windowSeconds <= 0) {
            return DetectionResult.none();
        }

        Instant until = context.occurredAt();
        Instant since = until.minusSeconds(windowSeconds);

        long totalBytes = Math.max(
            networkUsageEventRepository.sumTotalBytesInWindow(context.endpointId(), since, until), 0L);

        // long arithmetic: Integer.MAX_VALUE MiB still fits comfortably in a long.
        long thresholdBytes = thresholdMib.longValue() * BYTES_PER_MIB;

        if (totalBytes < thresholdBytes) {
            return DetectionResult.none();
        }

        String title = "Excessive network usage detected";
        String description = ("%d bytes (%d MiB) sent and received by endpoint %s within the last %d seconds "
            + "(threshold: %d MiB).")
            .formatted(totalBytes, totalBytes / BYTES_PER_MIB, context.endpointId(),
                windowSeconds, thresholdMib);

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
