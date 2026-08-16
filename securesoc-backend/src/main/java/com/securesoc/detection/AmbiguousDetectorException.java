package com.securesoc.detection;

import com.securesoc.entity.DetectionRule;

/**
 * Thrown by {@link DetectionEngine} when more than one {@link Detector}
 * bean reports {@code supports(rule) == true} for the same
 * {@code DetectionRule}. Detector routing must be deterministic - silently
 * picking one (e.g. by Spring bean registration order) is not acceptable
 * for a security detection engine, so this fails loudly instead.
 *
 * Resolving this means making the detectors' {@code supports(...)} checks
 * mutually exclusive (e.g. by rule type and/or event source), not adding
 * priority/ordering logic to {@link DetectionEngine}.
 */
public class AmbiguousDetectorException extends RuntimeException {

    public AmbiguousDetectorException(DetectionRule rule, int matchingDetectorCount) {
        super(("Ambiguous detector routing for rule '%s' (id=%s, ruleType=%s, eventSource=%s): "
                + "%d detectors report supports(rule) == true, expected at most 1.")
            .formatted(rule.getName(), rule.getId(), rule.getRuleType(), rule.getEventSource(),
                matchingDetectorCount));
    }
}
