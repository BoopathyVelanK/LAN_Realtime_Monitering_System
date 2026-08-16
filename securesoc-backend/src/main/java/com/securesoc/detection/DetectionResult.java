package com.securesoc.detection;

import com.securesoc.entity.DetectionRule;

import java.util.UUID;

/**
 * Outcome of a {@link Detector} evaluating a {@code DetectionRule} against a
 * {@link DetectionContext}. This is intentionally not a JPA entity and does
 * not create/reference an {@code Alert} row - it only carries what a future
 * AlertService would need to build one. Severity reuses
 * {@code DetectionRule.Severity} rather than introducing a duplicate enum,
 * since severity for a detection outcome originates from the matched rule.
 *
 * Use {@link #none()} when a detector evaluated a rule and found nothing -
 * detectors return this instead of {@code null} so callers never need a
 * null check on the result itself.
 */
public record DetectionResult(
    boolean detected,
    UUID ruleId,
    DetectionRule.Severity severity,
    String title,
    String description,
    UUID userId,
    UUID endpointId
) {

    public static DetectionResult none() {
        return new DetectionResult(false, null, null, null, null, null, null);
    }
}
