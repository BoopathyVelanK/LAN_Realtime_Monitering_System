package com.securesoc.detection;

import com.securesoc.entity.DetectionRule;
import com.securesoc.repository.DetectionRuleRepository;
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
 * </ol>
 *
 * This class deliberately contains no detector-specific logic (thresholds,
 * time windows, severities) - those live in {@code DetectionRule} rows and
 * in concrete {@link Detector} implementations. Adding a new detector (e.g.
 * the future RepeatedFailedLoginDetector) only means registering another
 * {@code Detector} bean; this class does not need to change.
 *
 * Read-only: this foundation does not persist alerts, risk scores, or
 * publish anything - that is future AlertService/RiskEngine/WebSocket work.
 */
@Service
public class DetectionEngine {

    private final DetectionRuleRepository detectionRuleRepository;
    private final List<Detector> detectors;

    public DetectionEngine(DetectionRuleRepository detectionRuleRepository, List<Detector> detectors) {
        this.detectionRuleRepository = detectionRuleRepository;
        this.detectors = detectors;
    }

    @Transactional(readOnly = true)
    public List<DetectionResult> evaluate(DetectionContext context) {
        List<DetectionRule> rules = detectionRuleRepository.findByEventSourceAndEnabledTrue(context.eventSource());

        return rules.stream()
            .map(rule -> findDetector(rule).map(detector -> detector.evaluate(context, rule)))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(DetectionResult::detected)
            .toList();
    }

    private Optional<Detector> findDetector(DetectionRule rule) {
        return detectors.stream().filter(detector -> detector.supports(rule)).findFirst();
    }
}
