package com.securesoc.detection;

import com.securesoc.entity.DetectionRule;
import com.securesoc.repository.UsbEventRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class UsbEventDetector implements Detector {

    private static final String EVENT_SOURCE = "USB_EVENT";

    private final UsbEventRepository usbEventRepository;

    public UsbEventDetector(UsbEventRepository usbEventRepository) {
        this.usbEventRepository = usbEventRepository;
    }

    @Override
    public boolean supports(DetectionRule rule) {
        return rule != null
            && rule.getRuleType() == DetectionRule.RuleType.THRESHOLD
            && EVENT_SOURCE.equals(rule.getEventSource());
    }

    @Override
    public DetectionResult evaluate(DetectionContext context, DetectionRule rule) {
        if (!supports(rule)) {
            return DetectionResult.none();
        }

        if (context.endpointId() == null) {
            return DetectionResult.none();
        }

        if (context.occurredAt() == null) {
            return DetectionResult.none();
        }

        Integer threshold = rule.getThreshold();
        if (threshold == null || threshold <= 0) {
            return DetectionResult.none();
        }

        Integer windowSeconds = rule.getWindowSeconds();
        if (windowSeconds == null || windowSeconds <= 0) {
            return DetectionResult.none();
        }

        Instant since = context.occurredAt().minusSeconds(windowSeconds);
        long count = usbEventRepository.countByEndpoint_IdAndEventTimeAfter(context.endpointId(), since);

        if (count < threshold) {
            return DetectionResult.none();
        }

        String title = "Abnormal USB activity detected";
        String description = "%d USB events observed for endpoint %s within the last %d seconds (threshold: %d)."
            .formatted(count, context.endpointId(), windowSeconds, threshold);

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
