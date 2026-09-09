package com.securesoc.service;

import com.securesoc.detection.DetectionContext;
import com.securesoc.detection.DetectionEngine;
import com.securesoc.detection.DetectionResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Isolates a full {@code DetectionEngine.evaluate(...)} call - detection,
 * alert persistence, and risk scoring together - in its own
 * {@code REQUIRES_NEW} transaction, deliberately separate from whatever
 * transaction the caller is running in.
 *
 * Why this exists: {@code DetectionEngine.evaluate()} is itself
 * {@code @Transactional} with the default REQUIRED propagation, so a
 * caller that is already inside its own {@code @Transactional} method
 * (e.g. {@code MonitoringService.recordUsb()}, after already persisting
 * the telemetry row that triggered detection) would otherwise have
 * {@code evaluate()} simply join that same physical transaction. Within
 * that shared transaction, {@code AlertService.createAlertFrom} and
 * {@code RiskScoreService.recordDetection} can both perform plain,
 * non-flushing {@code repository.save(...)} calls (this is exactly what
 * happens for every USB_EVENT detection today, since USB detections always
 * carry a null userId and therefore never take AlertService's
 * REQUIRES_NEW-protected dedup-insert path - see AlertInsertExecutor's
 * javadoc for that specific, narrower isolation). A buffered write like
 * that is not flushed until the caller's own later, unrelated commit -
 * meaning a constraint violation surfacing only at that point would roll
 * back the caller's entire transaction, silently discarding telemetry
 * that had already been successfully ingested and flushed.
 *
 * Running the whole evaluate() call here, in REQUIRES_NEW, closes that
 * gap without an explicit flush call: Spring's transactional interceptor
 * commits (and therefore flushes) this method's nested transaction
 * synchronously, before control returns to whatever called
 * {@link #evaluate}. That means:
 *   - any exception raised during detection/alert/risk processing, or
 *     during this nested transaction's own commit-time flush, surfaces
 *     here as a normal exception to the caller - never deferred until the
 *     caller's own later commit;
 *   - only this small nested transaction is rolled back on failure; the
 *     caller's transaction (and its EntityManager) is completely
 *     unaffected and can commit normally regardless of what happened here.
 *
 * Callers that want detection failures to be non-fatal to their own work
 * (e.g. {@code MonitoringService.recordUsb()}) must call this method
 * inside their own try/catch - this class only provides the transaction
 * boundary, not the failure-isolation policy, matching how
 * {@code AuthService.login()} already handles its own (differently-shaped)
 * direct call to {@code DetectionEngine.evaluate()}.
 */
@Component
public class DetectionEvaluationExecutor {

    private final DetectionEngine detectionEngine;

    public DetectionEvaluationExecutor(DetectionEngine detectionEngine) {
        this.detectionEngine = detectionEngine;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<DetectionResult> evaluate(DetectionContext context) {
        return detectionEngine.evaluate(context);
    }
}
