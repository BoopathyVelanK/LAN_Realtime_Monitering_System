package com.securesoc.detection;

import com.securesoc.entity.DetectionRule;
import com.securesoc.repository.DetectionRuleRepository;
import com.securesoc.service.AlertService;
import com.securesoc.service.RiskScoreService;
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
 *   <li>Hands each detected result to {@link RiskScoreService} so it can
 *       be applied to the endpoint's {@code RiskScore}.</li>
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
 * Alert persistence and risk scoring are each entirely their own service's
 * responsibility - this class never touches {@code AlertRepository} or
 * {@code RiskScoreRepository} directly, and does nothing beyond detection +
 * handing off detected results to both. The two are deliberately
 * independent side effects of the same detected result: {@link AlertService}
 * may deduplicate a detection against an existing OPEN alert, but
 * {@link RiskScoreService} is still called for every detected result
 * regardless of what {@code AlertService} did with it - see
 * {@link RiskScoreService}'s own javadoc for why. Neither call is
 * conditioned on the other's outcome. WebSocket notification remains
 * future work.
 */
@Service
public class DetectionEngine {

    private final DetectionRuleRepository detectionRuleRepository;
    private final List<Detector> detectors;
    private final AlertService alertService;
    private final RiskScoreService riskScoreService;

    public DetectionEngine(
        DetectionRuleRepository detectionRuleRepository,
        List<Detector> detectors,
        AlertService alertService,
        RiskScoreService riskScoreService
    ) {
        this.detectionRuleRepository = detectionRuleRepository;
        this.detectors = detectors;
        this.alertService = alertService;
        this.riskScoreService = riskScoreService;
    }

    /**
     * Evaluates {@code context} against every enabled rule for its event
     * source, and for each detected result: persists an Alert (via
     * {@link AlertService}) and applies the detection to the endpoint's
     * risk score (via {@link RiskScoreService}). Not read-only, unlike the
     * pre-AlertService version of this method, since it now writes through
     * both services' own {@code @Transactional} persistence.
     *
     * The two calls are deliberately separate, independent loops over
     * {@code detectedResults} - not combined into one {@code forEach} -
     * so that risk scoring never depends on, or is skipped based on,
     * whatever {@link AlertService} did (created a new alert, reused an
     * existing OPEN one for dedup, etc.). Every detected result reaches
     * {@link RiskScoreService#recordDetection}, unconditionally.
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
        detectedResults.forEach(riskScoreService::recordDetection);

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
