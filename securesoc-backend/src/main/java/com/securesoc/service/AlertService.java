package com.securesoc.service;

import com.securesoc.detection.DetectionResult;
import com.securesoc.entity.Alert;
import com.securesoc.entity.DetectionRule;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.User;
import com.securesoc.exception.ResourceNotFoundException;
import com.securesoc.repository.AlertRepository;
import com.securesoc.repository.DetectionRuleRepository;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Converts a detected {@link DetectionResult} into a persisted {@link Alert}.
 *
 * This service only creates/persists Alert rows. It does not run RiskEngine,
 * publish WebSocket notifications, or implement acknowledgement/assignment/
 * resolution - those are later phases (see DetectionResult's own javadoc,
 * which already anticipated this class).
 *
 * Alert deduplication: when a DetectionResult carries a non-null userId,
 * at most one OPEN alert may exist for that (userId, ruleId) pair - see
 * {@link #createOrReuseForUser(Alert, UUID, UUID)} and
 * V8__add_alert_open_dedup_index.sql. endpointId is intentionally not
 * part of the dedup key. Results with a null userId always create a new
 * Alert, unaffected by deduplication.
 */
@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final DetectionRuleRepository detectionRuleRepository;
    private final UserRepository userRepository;
    private final EndpointDeviceRepository endpointDeviceRepository;
    private final AlertInsertExecutor alertInsertExecutor;

    public AlertService(
        AlertRepository alertRepository,
        DetectionRuleRepository detectionRuleRepository,
        UserRepository userRepository,
        EndpointDeviceRepository endpointDeviceRepository,
        AlertInsertExecutor alertInsertExecutor
    ) {
        this.alertRepository = alertRepository;
        this.detectionRuleRepository = detectionRuleRepository;
        this.userRepository = userRepository;
        this.endpointDeviceRepository = endpointDeviceRepository;
        this.alertInsertExecutor = alertInsertExecutor;
    }

    /**
     * Persists an Alert for a detected DetectionResult.
     *
     * Returns {@code Optional.empty()} without touching the database when
     * {@code result} is {@code null}, {@link DetectionResult#none()}, or
     * otherwise has {@code detected() == false} - that is the normal,
     * expected "nothing to alert on" outcome, not an error.
     *
     * Referenced entities (rule, user, endpoint) are always resolved
     * through their repositories rather than constructed as fake/detached
     * references, so a stale or bogus id can never silently become an
     * invalid foreign key on the saved Alert.
     *
     * @throws ResourceNotFoundException if {@code result.ruleId()} does not
     *         resolve to an existing DetectionRule, or if a present (i.e.
     *         non-null) {@code userId}/{@code endpointId} does not resolve
     *         to an existing User/EndpointDevice. userId and endpointId
     *         remain unset on the Alert (matching their nullable
     *         ManyToOne mapping) only when the DetectionResult itself
     *         didn't supply one - never as a fallback for a lookup miss.
     */
    @Transactional
    public Optional<Alert> createAlertFrom(DetectionResult result) {
        if (result == null || !result.detected()) {
            return Optional.empty();
        }

        DetectionRule rule = detectionRuleRepository.findById(result.ruleId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "DetectionRule not found: " + result.ruleId()));

        Alert alert = new Alert();
        alert.setRule(rule);
        alert.setSeverity(mapSeverity(result.severity()));
        alert.setTitle(result.title());
        alert.setDescription(result.description());

        User user = null;
        if (result.userId() != null) {
            user = userRepository.findById(result.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "User not found: " + result.userId()));
            alert.setUser(user);
        }

        if (result.endpointId() != null) {
            EndpointDevice endpoint = endpointDeviceRepository.findById(result.endpointId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "EndpointDevice not found: " + result.endpointId()));
            alert.setEndpoint(endpoint);
        }

        if (user == null) {
            // No user to deduplicate against - create normally, exactly as
            // before deduplication existed.
            return Optional.of(alertRepository.save(alert));
        }

        return Optional.of(createOrReuseForUser(alert, user.getId(), rule.getId()));
    }

    /**
     * Deduplication for user-scoped alerts: at most one OPEN alert may
     * exist for a given (user_id, rule_id) pair (see
     * V8__add_alert_open_dedup_index.sql). ACKNOWLEDGED and RESOLVED
     * alerts never suppress a new one - only an existing OPEN alert does,
     * since the lookup below filters on OPEN specifically.
     *
     * The lookup is an optimization only, not the source of truth: it
     * avoids an unnecessary insert attempt in the common (non-racing)
     * case, but a concurrent request can always slip a conflicting insert
     * in between this check and the insert attempt below. The database's
     * partial unique index is what actually guarantees the invariant;
     * this method's job is to handle losing that race gracefully by
     * reusing whatever the other transaction committed, rather than
     * surfacing a raw constraint-violation error to the caller.
     *
     * The insert attempt itself is delegated to {@link AlertInsertExecutor},
     * which runs in its own REQUIRES_NEW transaction - see that class's
     * javadoc for why that isolation is required given how
     * {@code DetectionEngine.evaluate()} calls this method.
     */
    private Alert createOrReuseForUser(Alert alert, UUID userId, UUID ruleId) {
        Optional<Alert> existing =
            alertRepository.findByUser_IdAndRule_IdAndStatus(userId, ruleId, Alert.Status.OPEN);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            return alertInsertExecutor.insertAlert(alert);
        } catch (DataIntegrityViolationException raceLoss) {
            // A concurrent request won the race and committed an OPEN
            // alert for the same (user_id, rule_id) between our check
            // above and our insert attempt just now. alertInsertExecutor
            // ran in its own REQUIRES_NEW transaction, so only that
            // nested transaction rolled back - this (outer) transaction
            // and its EntityManager are untouched and safe to keep using.
            return alertRepository.findByUser_IdAndRule_IdAndStatus(userId, ruleId, Alert.Status.OPEN)
                .orElseThrow(() -> raceLoss);
        }
    }

    /** DetectionResult carries DetectionRule.Severity (see that record's
     * javadoc for why), while Alert has its own identical-valued Severity
     * enum - this maps between the two by name rather than introducing a
     * shared enum type, since that's a bigger change than this task's scope. */
    private static Alert.Severity mapSeverity(DetectionRule.Severity severity) {
        return severity == null ? null : Alert.Severity.valueOf(severity.name());
    }
}
