package com.securesoc.repository;

import com.securesoc.entity.RunningAppSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface RunningAppSnapshotRepository extends JpaRepository<RunningAppSnapshot, UUID> {

    Page<RunningAppSnapshot> findAllByOrderByCapturedAtDesc(Pageable pageable);

    Page<RunningAppSnapshot> findByEndpoint_IdOrderByCapturedAtDesc(UUID endpointId, Pageable pageable);

    /** Backs MonitoringService.listRunningAppSnapshots for a Faculty caller
     * with no endpointId filter - scoped to the caller's authorized labs
     * (see FacultyScopeService), never an unscoped page filtered in Java. */
    Page<RunningAppSnapshot> findByEndpoint_Lab_IdInOrderByCapturedAtDesc(Collection<UUID> labIds, Pageable pageable);
}
