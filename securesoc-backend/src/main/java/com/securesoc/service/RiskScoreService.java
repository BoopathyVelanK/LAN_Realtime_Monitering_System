package com.securesoc.service;

import com.securesoc.detection.DetectionResult;
import com.securesoc.dto.RiskScoreResponse;
import com.securesoc.entity.DetectionRule;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.RiskScore;
import com.securesoc.exception.ResourceNotFoundException;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.repository.RiskScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Applies detected {@link DetectionResult}s to an endpoint's {@link RiskScore}.
 *
 * This is Checkpoint A of the Risk Engine only. Scope, deliberately:
 * <ul>
 *   <li>Reads/writes {@link RiskScoreRepository} and {@link EndpointDeviceRepository}
 *       only - no new repository methods, no new entity fields (no
 *       {@code @Version}), no migration.</li>
 *   <li>Wired into {@link com.securesoc.detection.DetectionEngine} as of
 *       Checkpoint B (see {@code DetectionEngineTest},
 *       {@code RiskScorePersistenceIntegrationTest}). The read methods below
 *       ({@link #getAll()}, {@link #getForEndpoint(UUID)}) and
 *       {@code RiskScoreController}/{@code RiskScoreResponse} are
 *       Checkpoint C - read-only exposure of already-persisted scores, no
 *       change to {@link #recordDetection} or the scoring formula.</li>
 * </ul>
 *
 * Design decisions (approved proposal - see project risk-engine design doc;
 * restated here since they materially shape this class's behavior):
 * <ul>
 *   <li><b>Additive scoring:</b> every detected result adds points for its
 *       severity to the endpoint's current score. This happens for every
 *       detection, independent of whether {@link AlertService} deduplicated
 *       the corresponding {@code Alert} - RiskScore and Alert answer
 *       different questions (cumulative risk vs. notification noise) and
 *       are intentionally decoupled.</li>
 *   <li><b>Severity -&gt; points:</b> LOW=5, MEDIUM=15, HIGH=30, CRITICAL=50.</li>
 *   <li><b>Clamping:</b> score is always clamped to [0, 100], matching the
 *       {@code risk_scores.score} DB CHECK constraint.</li>
 *   <li><b>Level thresholds:</b> SAFE &lt; 15, LOW 15-34, MEDIUM 35-59,
 *       HIGH 60-79, CRITICAL 80-100. Level is entirely derived from score;
 *       it is never set independently.</li>
 *   <li><b>No decay:</b> nothing in this class ever reduces a score over
 *       time. A score only changes when a new detection is recorded.</li>
 *   <li><b>endpointId == null is a no-op:</b> {@code risk_scores.endpoint_id}
 *       is NOT NULL with a unique index in the current schema, so there is
 *       no valid row to write to when a DetectionResult carries no
 *       endpoint (e.g. a portal-login-only detection). This method returns
 *       {@link Optional#empty()} without touching either repository in
 *       that case - it does not throw, since a null endpointId is an
 *       expected, valid shape for some detections (see
 *       {@code RepeatedFailedLoginDetector}), not an error.</li>
 *   <li><b>Alert acknowledgement/resolution never changes RiskScore:</b>
 *       this class has no method that reads or reacts to {@code Alert}
 *       status at all - there is nothing here to wire up even accidentally.
 *       RiskScore reflects cumulative historical signal, not currently-open
 *       alert count.</li>
 *   <li><b>No optimistic locking:</b> per the approved proposal, this
 *       checkpoint does not add a {@code @Version} field to {@link RiskScore}.
 *       A concurrent read-modify-write race on the same endpoint's score
 *       is accepted as last-write-wins for now.</li>
 * </ul>
 */
@Service
public class RiskScoreService {

    static final int MIN_SCORE = 0;
    static final int MAX_SCORE = 100;

    private final RiskScoreRepository riskScoreRepository;
    private final EndpointDeviceRepository endpointDeviceRepository;

    public RiskScoreService(
        RiskScoreRepository riskScoreRepository,
        EndpointDeviceRepository endpointDeviceRepository
    ) {
        this.riskScoreRepository = riskScoreRepository;
        this.endpointDeviceRepository = endpointDeviceRepository;
    }

    /**
     * Records one detected result against its endpoint's risk score.
     *
     * Returns {@link Optional#empty()} without touching either repository
     * when {@code result} is {@code null}, {@link DetectionResult#none()},
     * otherwise has {@code detected() == false}, or has a {@code null}
     * {@code endpointId} - all three are normal "nothing to score" outcomes,
     * not errors (mirrors {@link AlertService#createAlertFrom} in spirit,
     * though the null-endpoint case here is a hard schema constraint, not
     * a stylistic choice - see class javadoc).
     *
     * When an endpoint has no existing {@link RiskScore} row, one is
     * created starting from a score of 0 before this detection's points
     * are applied - the endpoint itself is resolved through
     * {@link EndpointDeviceRepository} (never constructed as a fake/detached
     * reference), matching how {@link AlertService} resolves its own
     * entity references.
     *
     * @throws ResourceNotFoundException if {@code result.endpointId()} is
     *         non-null but does not resolve to an existing
     *         {@code EndpointDevice} when a new {@code RiskScore} row would
     *         need to be created for it.
     */
    @Transactional
    public Optional<RiskScore> recordDetection(DetectionResult result) {
        if (result == null || !result.detected() || result.endpointId() == null) {
            return Optional.empty();
        }

        RiskScore riskScore = riskScoreRepository.findByEndpoint_Id(result.endpointId())
            .orElseGet(() -> newRiskScoreFor(result.endpointId()));

        int updatedScore = clamp(riskScore.getScore() + pointsFor(result.severity()));
        riskScore.setScore((short) updatedScore);
        riskScore.setLevel(levelFor(updatedScore));

        return Optional.of(riskScoreRepository.save(riskScore));
    }

    /**
     * Returns every persisted {@link RiskScore}, ordered by score descending
     * (highest-risk endpoints first) - backs {@code GET /risk-scores}.
     * Read-only; never creates a row. An endpoint with no detections yet
     * simply has no row and is absent from this list (see class javadoc:
     * this checkpoint deliberately does not synthesize a default
     * SAFE/0 entry for endpoints that have never been scored).
     */
    @Transactional(readOnly = true)
    public List<RiskScoreResponse> getAll() {
        return riskScoreRepository.findAllByOrderByScoreDesc().stream()
            .map(RiskScoreService::toResponse)
            .toList();
    }

    /**
     * Returns the persisted {@link RiskScore} for one endpoint - backs
     * {@code GET /risk-scores/{endpointId}}. Read-only; never creates a row.
     *
     * @throws ResourceNotFoundException if {@code endpointId} has no
     *         persisted {@code RiskScore} row (never detected against, or
     *         the endpoint itself doesn't exist) - deliberately not
     *         distinguished from each other, and deliberately not masked
     *         by a synthetic SAFE/0 response (see class javadoc).
     */
    @Transactional(readOnly = true)
    public RiskScoreResponse getForEndpoint(UUID endpointId) {
        return riskScoreRepository.findByEndpoint_Id(endpointId)
            .map(RiskScoreService::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("RiskScore not found for endpoint: " + endpointId));
    }

    static RiskScoreResponse toResponse(RiskScore r) {
        return new RiskScoreResponse(
            r.getEndpoint().getId(),
            r.getScore(),
            r.getLevel().name(),
            r.getUpdatedAt()
        );
    }

    private RiskScore newRiskScoreFor(UUID endpointId) {
        EndpointDevice endpoint = endpointDeviceRepository.findById(endpointId)
            .orElseThrow(() -> new ResourceNotFoundException("EndpointDevice not found: " + endpointId));

        RiskScore riskScore = new RiskScore();
        riskScore.setEndpoint(endpoint);
        riskScore.setScore((short) 0);
        riskScore.setLevel(RiskScore.Level.SAFE);
        return riskScore;
    }

    /** Severity -&gt; points mapping (approved proposal).
     *
     * No null-severity branch: {@code DetectionRule.severity} is
     * {@code @Column(nullable = false)} (see
     * V5__phase4_detection_foundation.sql / DetectionRule.java), and the
     * only place a {@code detected() == true} DetectionResult is built
     * today (RepeatedFailedLoginDetector) passes {@code rule.getSeverity()}
     * straight through - never a literal or derived null. A null severity
     * reaching this method would mean that invariant was violated
     * elsewhere, which should fail loudly (NullPointerException from the
     * switch below) rather than silently recording a zero-point risk
     * event that would misrepresent what happened. */
    private static int pointsFor(DetectionRule.Severity severity) {
        return switch (severity) {
            case LOW -> 5;
            case MEDIUM -> 15;
            case HIGH -> 30;
            case CRITICAL -> 50;
        };
    }

    private static int clamp(int score) {
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, score));
    }

    /** Level thresholds (approved proposal): SAFE &lt; 15, LOW 15-34,
     * MEDIUM 35-59, HIGH 60-79, CRITICAL 80-100. */
    private static RiskScore.Level levelFor(int score) {
        if (score >= 80) {
            return RiskScore.Level.CRITICAL;
        }
        if (score >= 60) {
            return RiskScore.Level.HIGH;
        }
        if (score >= 35) {
            return RiskScore.Level.MEDIUM;
        }
        if (score >= 15) {
            return RiskScore.Level.LOW;
        }
        return RiskScore.Level.SAFE;
    }
}
