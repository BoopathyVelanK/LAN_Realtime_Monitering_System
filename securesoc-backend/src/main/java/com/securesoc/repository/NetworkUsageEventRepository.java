package com.securesoc.repository;

import com.securesoc.entity.NetworkUsageEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface NetworkUsageEventRepository extends JpaRepository<NetworkUsageEvent, UUID> {

    Page<NetworkUsageEvent> findAllByOrderByRecordedAtDesc(Pageable pageable);

    Page<NetworkUsageEvent> findByEndpoint_IdOrderByRecordedAtDesc(UUID endpointId, Pageable pageable);

    /** Backs MonitoringService.listNetworkUsageEvents for a Faculty caller
     * with no endpointId filter - scoped to the caller's authorized labs
     * (see FacultyScopeService), never an unscoped page filtered in Java. */
    Page<NetworkUsageEvent> findByEndpoint_Lab_IdInOrderByRecordedAtDesc(Collection<UUID> labIds, Pageable pageable);
}
