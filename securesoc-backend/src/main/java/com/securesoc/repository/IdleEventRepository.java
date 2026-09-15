package com.securesoc.repository;

import com.securesoc.entity.IdleEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface IdleEventRepository extends JpaRepository<IdleEvent, UUID> {

    Page<IdleEvent> findAllByOrderByRecordedAtDesc(Pageable pageable);

    Page<IdleEvent> findByEndpoint_IdOrderByRecordedAtDesc(UUID endpointId, Pageable pageable);

    /** Backs MonitoringService.listIdleEvents for a Faculty caller with no
     * endpointId filter - scoped to the caller's authorized labs (see
     * FacultyScopeService), never an unscoped page filtered in Java. */
    Page<IdleEvent> findByEndpoint_Lab_IdInOrderByRecordedAtDesc(Collection<UUID> labIds, Pageable pageable);
}
