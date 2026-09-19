package com.securesoc.repository;

import com.securesoc.entity.InternetUsageEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface InternetUsageEventRepository extends JpaRepository<InternetUsageEvent, UUID> {

    Page<InternetUsageEvent> findAllByOrderByRecordedAtDesc(Pageable pageable);

    Page<InternetUsageEvent> findByEndpoint_IdOrderByRecordedAtDesc(UUID endpointId, Pageable pageable);

    /** Backs MonitoringService.listInternetUsageEvents for a Faculty caller
     * with no endpointId filter - scoped to the caller's authorized labs
     * (see FacultyScopeService), never an unscoped page filtered in Java. */
    Page<InternetUsageEvent> findByEndpoint_Lab_IdInOrderByRecordedAtDesc(Collection<UUID> labIds, Pageable pageable);
}
