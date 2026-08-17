package com.securesoc.detection;

import com.securesoc.entity.DetectionRule;
import com.securesoc.repository.AuthFailureEventRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Detects repeated authentication failures for a single user within a
 * configured time window. Supports threshold-based {@code DetectionRule}s
 * whose {@code eventSource} is {@value #EVENT_SOURCE} - the auth-failure
 * event source name documented in
 * V5__phase4_detection_foundation.sql ({@code event_source ... -- e.g.
 * 'AUTH_FAILURE', 'USB_EVENT'}).
 *
 * This class only detects; it does not persist an {@code Alert}, adjust
 * risk scores, or publish anything. That is future AlertService/RiskEngine/
 * WebSocket work.
 */
@Component
public class RepeatedFailedLoginDetector implements Detector {

    private static final String EVENT_SOURCE = "AUTH_FAILURE";

    private final AuthFailureEventRepository authFailureEventRepository;

    public RepeatedFailedLoginDetector(AuthFailureEventRepository authFailureEventRepository) {
        this.authFailureEventRepository = authFailureEventRepository;
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

        if (context.userId() == null) {
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
        long failureCount = authFailureEventRepository.countByUser_IdAndAttemptedAtAfter(context.userId(), since);

        if (failureCount < threshold) {
            return DetectionResult.none();
        }

        String title = "Repeated failed login attempts detected";
        String description = "%d failed login attempts observed for user %s within the last %d seconds (threshold: %d)."
            .formatted(failureCount, context.userId(), windowSeconds, threshold);

        return new DetectionResult(
            true,
            rule.getId(),
            rule.getSeverity(),
            title,
            description,
            context.userId(),
            context.endpointId()
        );
    }
}
