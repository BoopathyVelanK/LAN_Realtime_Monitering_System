package com.securesoc.repository;

import com.securesoc.entity.UsbEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface UsbEventRepository extends JpaRepository<UsbEvent, UUID> {

    Page<UsbEvent> findAllByOrderByEventTimeDesc(Pageable pageable);

    Page<UsbEvent> findByEndpoint_IdOrderByEventTimeDesc(UUID endpointId, Pageable pageable);

    long countByEndpoint_IdAndEventTimeAfter(UUID endpointId, java.time.Instant since);

    /** Backs MonitoringService.listUsbEvents for a Faculty caller with no
     * endpointId filter - scoped to the caller's authorized labs (see
     * FacultyScopeService), never an unscoped page filtered in Java. */
    Page<UsbEvent> findByEndpoint_Lab_IdInOrderByEventTimeDesc(Collection<UUID> labIds, Pageable pageable);
}
