package com.securesoc.repository;

import com.securesoc.entity.VpnEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface VpnEventRepository extends JpaRepository<VpnEvent, UUID> {

    Page<VpnEvent> findAllByOrderByDetectedAtDesc(Pageable pageable);

    Page<VpnEvent> findByEndpoint_IdOrderByDetectedAtDesc(UUID endpointId, Pageable pageable);

    /** Backs MonitoringService.listVpnEvents for a Faculty caller with no
     * endpointId filter - scoped to the caller's authorized labs (see
     * FacultyScopeService), never an unscoped page filtered in Java. */
    Page<VpnEvent> findByEndpoint_Lab_IdInOrderByDetectedAtDesc(Collection<UUID> labIds, Pageable pageable);
}
