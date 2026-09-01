package com.securesoc.service;

import com.securesoc.detection.DetectionResult;
import com.securesoc.dto.AlertResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Converts a detected {@link DetectionResult} into a persisted {@link Alert}.
 *
 * Alert deduplication: when a DetectionResult carries a non-null userId,
 * at most one OPEN alert may exist for that (userId, ruleId) pair - see
 * {@link #createOrReuseForUser(Alert, UUID, UUID)} and
 * V8__add_alert_open_dedup_index.sql. endpointId is intentionally not
 * part of the dedup key. Results with a null userId always create a new
 * Alert, unaffected by deduplication.
 *
 * After each successful persistence, the resulting {@link AlertResponse} is
 * handed to {@link WebSocketAlertEventPublisher}, which publishes it to
 * {@code /topic/alerts} after the enclosing transaction commits — never
 * before. For dedup-reused alerts (createOrReuseForUser returning an
 * existing OPEN alert without a new INSERT) no WebSocket event is published,
 * since the alert state did not change.
 */
@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final DetectionRuleRepository detectionRuleRepository;
    private final UserRepository userRepository;
    private final EndpointDeviceRepository endpointDeviceRepository;
    private final AlertInsertExecutor alertInsertExecutor;
    private final WebSocketAlertEventPublisher alertPublisher;

    public AlertService(
        AlertRepository alertRepository,
        DetectionRuleRepository detectionRuleRepository,
        UserRepository userRepository,
        EndpointDeviceRepository endpointDeviceRepository,
        AlertInsertExecutor alertInsertExecutor,
        WebSocketAlertEventPublisher alertPublisher
    ) {
        this.alertRepository = alertRepository;
        this.detectionRuleRepository = detectionRuleRepository;
        this.userRepository = userRepository;
        this.endpointDeviceRepository = endpointDeviceRepository;
        this.alertInsertExecutor = alertInsertExecutor;
        this.alertPublisher = alertPublisher;
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
     * When a new Alert is successfully persisted, its {@link AlertResponse}
     * is published to {@code /topic/alerts} via {@link WebSocketAlertEventPublisher}
     * after the transaction commits. Dedup-reused existing OPEN alerts do
     * not trigger a publish (no state change occurred).
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

        Alert saved;
        boolean isNewAlert;
        if (user == null) {
            // No user to deduplicate against - create normally, exactly as
            // before deduplication existed.
            saved = alertRepository.save(alert);
            isNewAlert = true;
        } else {
            AlertOrReuse result2 = createOrReuseForUser(alert, user.getId(), rule.getId());
            saved = result2.alert();
            isNewAlert = result2.isNew();
        }

        if (isNewAlert) {
            alertPublisher.publishAlert(toResponse(saved));
        }

        return Optional.of(saved);
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
    private AlertOrReuse createOrReuseForUser(Alert alert, UUID userId, UUID ruleId) {
        Optional<Alert> existing =
            alertRepository.findByUser_IdAndRule_IdAndStatus(userId, ruleId, Alert.Status.OPEN);
        if (existing.isPresent()) {
            return new AlertOrReuse(existing.get(), false);
        }

        try {
            return new AlertOrReuse(alertInsertExecutor.insertAlert(alert), true);
        } catch (DataIntegrityViolationException raceLoss) {
            // A concurrent request won the race and committed an OPEN
            // alert for the same (user_id, rule_id) between our check
            // above and our insert attempt just now. alertInsertExecutor
            // ran in its own REQUIRES_NEW transaction, so only that
            // nested transaction rolled back - this (outer) transaction
            // and its EntityManager are untouched and safe to keep using.
            Alert reused = alertRepository.findByUser_IdAndRule_IdAndStatus(userId, ruleId, Alert.Status.OPEN)
                .orElseThrow(() -> raceLoss);
            return new AlertOrReuse(reused, false);
        }
    }

    /** Simple carrier: the saved alert and whether it was newly inserted (vs. dedup-reused). */
    private record AlertOrReuse(Alert alert, boolean isNew) {}

    /** DetectionResult carries DetectionRule.Severity (see that record's
     * javadoc for why), while Alert has its own identical-valued Severity
     * enum - this maps between the two by name rather than introducing a
     * shared enum type, since that's a bigger change than this task's scope. */
    private static Alert.Severity mapSeverity(DetectionRule.Severity severity) {
        return severity == null ? null : Alert.Severity.valueOf(severity.name());
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlerts(UUID endpointId, String statusStr) {
        Alert.Status status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = Alert.Status.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return List.of();
            }
        }

        Pageable limit = PageRequest.of(0, 100);
        Page<Alert> page;
        if (endpointId != null && status != null) {
            page = alertRepository.findByEndpoint_IdAndStatusOrderByCreatedAtDesc(endpointId, status, limit);
        } else if (endpointId != null) {
            page = alertRepository.findByEndpoint_IdOrderByCreatedAtDesc(endpointId, limit);
        } else if (status != null) {
            page = alertRepository.findByStatusOrderByCreatedAtDesc(status, limit);
        } else {
            page = alertRepository.findAllByOrderByCreatedAtDesc(limit);
        }
        return page.stream().map(AlertService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AlertResponse getAlertById(UUID id) {
        return alertRepository.findById(id)
            .map(AlertService::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + id));
    }

    @Transactional
    public AlertResponse acknowledgeAlert(UUID id, UUID userId) {
        Alert alert = alertRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + id));

        if (alert.getStatus() == Alert.Status.OPEN) {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
            alert.setStatus(Alert.Status.ACKNOWLEDGED);
            alert.setAcknowledgedBy(user);
            alert.setAcknowledgedAt(Instant.now());
            alert = alertRepository.save(alert);
        }
        AlertResponse response = toResponse(alert);
        alertPublisher.publishAlert(response);
        return response;
    }

    @Transactional
    public AlertResponse resolveAlert(UUID id) {
        Alert alert = alertRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + id));

        if (alert.getStatus() != Alert.Status.RESOLVED) {
            alert.setStatus(Alert.Status.RESOLVED);
            alert.setResolvedAt(Instant.now());
            alert = alertRepository.save(alert);
        }
        AlertResponse response = toResponse(alert);
        alertPublisher.publishAlert(response);
        return response;
    }

    static AlertResponse toResponse(Alert alert) {
        String category = alert.getRule() != null ? alert.getRule().getEventSource() : "UNKNOWN";
        Instant updatedAt = alert.getResolvedAt() != null ? alert.getResolvedAt() :
                           (alert.getAcknowledgedAt() != null ? alert.getAcknowledgedAt() : alert.getCreatedAt());

        return new AlertResponse(
            alert.getId(),
            alert.getEndpoint() != null ? alert.getEndpoint().getId() : null,
            alert.getEndpoint() != null ? alert.getEndpoint().getHostname() : null,
            category,
            alert.getSeverity() != null ? alert.getSeverity().name() : null,
            alert.getTitle(),
            alert.getDescription(),
            alert.getStatus() != null ? alert.getStatus().name() : null,
            null, // assignedToUserId
            null, // assignedToUsername
            alert.getAcknowledgedBy() != null ? alert.getAcknowledgedBy().getId() : null,
            alert.getAcknowledgedBy() != null ? alert.getAcknowledgedBy().getUsername() : null,
            alert.getAcknowledgedAt(),
            alert.getCreatedAt(),
            updatedAt,
            alert.getResolvedAt()
        );
    }
}
