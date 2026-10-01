package com.securesoc.repository;

import com.securesoc.entity.NetworkUsageEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface NetworkUsageEventRepository extends JpaRepository<NetworkUsageEvent, UUID> {

    Page<NetworkUsageEvent> findAllByOrderByRecordedAtDesc(Pageable pageable);

    Page<NetworkUsageEvent> findByEndpoint_IdOrderByRecordedAtDesc(UUID endpointId, Pageable pageable);

    /** Backs MonitoringService.listNetworkUsageEvents for a Faculty caller
     * with no endpointId filter - scoped to the caller's authorized labs
     * (see FacultyScopeService), never an unscoped page filtered in Java. */
    Page<NetworkUsageEvent> findByEndpoint_Lab_IdInOrderByRecordedAtDesc(Collection<UUID> labIds, Pageable pageable);

    /** Backs NetworkUsageDetector: total bytes (sent + received) for one
     * endpoint whose effective timestamp - COALESCE(sampledAt, recordedAt),
     * since sampledAt is nullable for legacy rows - is in the half-open
     * window (since, until]. The upper bound keeps a replayed (old
     * sampledAt, late recordedAt) sample from counting newer rows.
     * Each addend is clamped at zero so a negative stored value can never
     * reduce the total. Returns 0 when nothing matches. */
    @Query("""
        SELECT COALESCE(SUM(
            (CASE WHEN e.bytesSent > 0 THEN e.bytesSent ELSE 0L END)
          + (CASE WHEN e.bytesReceived > 0 THEN e.bytesReceived ELSE 0L END)), 0L)
        FROM NetworkUsageEvent e
        WHERE e.endpoint.id = :endpointId
          AND COALESCE(e.sampledAt, e.recordedAt) > :since
          AND COALESCE(e.sampledAt, e.recordedAt) <= :until
        """)
    long sumTotalBytesInWindow(@Param("endpointId") UUID endpointId,
                               @Param("since") Instant since,
                               @Param("until") Instant until);
}
