package com.securesoc.repository;

import com.securesoc.entity.RunningApp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface RunningAppRepository extends JpaRepository<RunningApp, UUID> {

    /** Backs SuspiciousProcessDetector's PROCESS_MATCH check: an exact,
     * case-insensitive process-name match for one endpoint, scoped to
     * running-app snapshots captured after a given instant.
     *
     * RunningApp has no timestamp of its own (see RunningApp's Javadoc) -
     * the relevant timestamp lives on its parent RunningAppSnapshot, so
     * this traverses snapshot -> endpoint and snapshot -> capturedAt
     * rather than any field directly on RunningApp. */
    boolean existsBySnapshot_Endpoint_IdAndProcessNameIgnoreCaseAndSnapshot_CapturedAtAfter(
        UUID endpointId, String processName, Instant since);
}
