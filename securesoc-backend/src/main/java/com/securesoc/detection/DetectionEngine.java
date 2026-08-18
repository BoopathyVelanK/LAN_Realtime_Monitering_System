package com.securesoc.detection;

import com.securesoc.entity.DetectionRule;
import com.securesoc.repository.DetectionRuleRepository;
import com.securesoc.service.AlertService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Orchestration layer for the detection foundation. Given an incoming
 * {@link DetectionContext}, it:
 *
 * <ol>
 *   <li>Looks up the enabled {@code DetectionRule}s for that context's
 *       event source (via {@code DetectionRuleRepository}).</li>
 *   <li>For each rule, finds the {@link Detector} bean that supports it.</li>
 *   <li>Delegates evaluation to that detector.</li>
 *   <li>Collects the {@link DetectionResult}s where something was actually
 *       detected.</li>
 *   <li>Hands each detected result to {@link AlertService} so it can be
 *       persisted as an {@code Alert}.</li>
 * </ol>
 *
 * This class deliberately contains no detector-specific logic (thresholds,
 * time windows, severities) - those live in {@code DetectionRule} rows and
 * in concrete {@link Detector} implementations. Adding a new detector only
 * means registering another {@code Detector} bean; this class does not
 * need to change.
 *
 * Detector routing is deterministic and safe by construction: a rule with
 * no supporting detector is simply skipped (no result invented), a rule
 * with exactly one supporting detector is evaluated normally, and a rule
 * with more than one supporting detector throws
 * {@link AmbiguousDetectorException} rather than silently picking one -
 * see {@link #findDetector(DetectionRule)}.
 *
 * Alert persistence itself is entirely {@link AlertService}'s
 * responsibility - this class never touches {@code AlertRepository}
 * directly, and does nothing beyond detection + handing off detected
 * results. RiskEngine and WebSocket notification remain future work.
 */
@Service
public class DetectionEngine {

    private final DetectionRuleRepository detectionRuleRepository;
    private final List<Detector> detectors;
    private final AlertService alertService;

    public DetectionEngine(
        DetectionRuleRepository detectionRuleRepository,
        List<Detector> detectors,
        AlertService alertService
    ) {
        this.detectionRuleRepository = detectionRuleRepository;
        this.detectors = detectors;
        this.alertService = alertService;
    }

    /**
     * Evaluates {@code context} against every enabled rule for its event
     * source, and persists an Alert (via {@link AlertService}) for each
     * detected result. Not read-only, unlike the pre-AlertService version
     * of this method, since it now writes through AlertService's own
     * {@code @Transactional} persistence.
     */
    @Transactional
    public List<DetectionResult> evaluate(DetectionContext context) {
        List<DetectionRule> rules = detectionRuleRepository.findByEventSourceAndEnabledTrue(context.eventSource());

        List<DetectionResult> detectedResults = rules.stream()
            .map(rule -> findDetector(rule).map(detector -> detector.evaluate(context, rule)))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(DetectionResult::detected)
            .toList();

        detectedResults.forEach(alertService::createAlertFrom);

        return detectedResults;
    }

    /**
     * Returns the single {@link Detector} that supports {@code rule}, or
     * {@link Optional#empty()} if none do. Throws
     * {@link AmbiguousDetectorException} if more than one detector reports
     * support for the same rule - callers must not fall back to picking
     * one, since that would make behavior depend on Spring bean ordering.
     */
    private Optional<Detector> findDetector(DetectionRule rule) {
        List<Detector> matching = detectors.stream()
            .filter(detector -> detector.supports(rule))
            .toList();

        if (matching.size() > 1) {
            throw new AmbiguousDetectorException(rule, matching.size());
        }

        return matching.isEmpty() ? Optional.empty() : Optional.of(matching.get(0));
    }
}
