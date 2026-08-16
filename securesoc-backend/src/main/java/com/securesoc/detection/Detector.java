package com.securesoc.detection;

import com.securesoc.entity.DetectionRule;

/**
 * Extension point for detection logic. A concrete detector (e.g. the future
 * RepeatedFailedLoginDetector) implements this to evaluate one class of
 * {@code DetectionRule} against a {@link DetectionContext}.
 *
 * A detector's responsibility is detection only: it must not persist an
 * {@code Alert} or {@code RiskScore}, publish WebSocket messages, or touch
 * authentication behavior. It returns a {@link DetectionResult} and leaves
 * everything downstream (alerting, risk scoring, notification) to later
 * layers.
 *
 * Implementations are expected to be Spring beans; {@link DetectionEngine}
 * collects all {@code Detector} beans and routes each enabled
 * {@code DetectionRule} to whichever one supports it.
 */
public interface Detector {

    /**
     * Whether this detector knows how to evaluate the given rule (typically
     * based on {@code rule.getRuleType()} and/or {@code rule.getEventSource()}).
     */
    boolean supports(DetectionRule rule);

    /**
     * Evaluates {@code context} against {@code rule}. Returns
     * {@link DetectionResult#none()} when the rule's condition was not met -
     * never {@code null}.
     */
    DetectionResult evaluate(DetectionContext context, DetectionRule rule);
}
