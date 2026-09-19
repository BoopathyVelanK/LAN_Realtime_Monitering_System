package com.securesoc.repository;

import com.securesoc.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    Page<Alert> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Alert> findByStatusOrderByCreatedAtDesc(Alert.Status status, Pageable pageable);

    Page<Alert> findByEndpoint_IdOrderByCreatedAtDesc(UUID endpointId, Pageable pageable);

    Page<Alert> findByEndpoint_IdAndStatusOrderByCreatedAtDesc(UUID endpointId, Alert.Status status, Pageable pageable);

    // --- Faculty scope resolution (AlertService.getAlerts) ---------------
    // Mirrors the pattern already established in EndpointDeviceRepository
    // for FacultyScopeService: the caller's already-resolved set of
    // authorized laboratory IDs is pushed into the WHERE clause, never
    // filtered in Java after an unscoped findAll(). An alert with no
    // endpoint (e.g. a portal-login alert) never matches either query
    // below, since it has no lab to match against - see AlertService's
    // scope javadoc for why that is the deliberate, fail-closed behavior
    // for Faculty callers.

    /** Backs AlertService.getAlerts for a Faculty caller with no status filter. */
    Page<Alert> findByEndpoint_Lab_IdInOrderByCreatedAtDesc(Collection<UUID> labIds, Pageable pageable);

    /** Backs AlertService.getAlerts for a Faculty caller with a status filter. */
    Page<Alert> findByEndpoint_Lab_IdInAndStatusOrderByCreatedAtDesc(
        Collection<UUID> labIds, Alert.Status status, Pageable pageable);

    /**
     * Alert deduplication lookup (see V8__add_alert_open_dedup_index.sql).
     * Deliberately keyed on (user_id, rule_id, status) only - endpoint_id
     * is NOT part of the dedup key, since a single user's repeated-login
     * alert, for example, may originate from a portal login with no
     * endpoint at all. Only ever called with a non-null userId; AlertService
     * skips deduplication entirely when DetectionResult.userId() is null.
     */
    Optional<Alert> findByUser_IdAndRule_IdAndStatus(UUID userId, UUID ruleId, Alert.Status status);
}
