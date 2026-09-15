package com.securesoc.repository;

import com.securesoc.entity.LoginEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface LoginEventRepository extends JpaRepository<LoginEvent, UUID> {

    Page<LoginEvent> findAllByOrderByLoginTimeDesc(Pageable pageable);

    Page<LoginEvent> findByEndpoint_IdOrderByLoginTimeDesc(UUID endpointId, Pageable pageable);

    /** Backs MonitoringService.listLoginEvents for a Faculty caller with no
     * endpointId filter - scoped to the caller's authorized labs (see
     * FacultyScopeService), never an unscoped page filtered in Java. */
    Page<LoginEvent> findByEndpoint_Lab_IdInOrderByLoginTimeDesc(Collection<UUID> labIds, Pageable pageable);
}
