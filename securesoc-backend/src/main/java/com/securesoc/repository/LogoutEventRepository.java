package com.securesoc.repository;

import com.securesoc.entity.LogoutEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface LogoutEventRepository extends JpaRepository<LogoutEvent, UUID> {

    Page<LogoutEvent> findAllByOrderByLogoutTimeDesc(Pageable pageable);

    Page<LogoutEvent> findByEndpoint_IdOrderByLogoutTimeDesc(UUID endpointId, Pageable pageable);

    /** Backs MonitoringService.listLogoutEvents for a Faculty caller with no
     * endpointId filter - scoped to the caller's authorized labs (see
     * FacultyScopeService), never an unscoped page filtered in Java. */
    Page<LogoutEvent> findByEndpoint_Lab_IdInOrderByLogoutTimeDesc(Collection<UUID> labIds, Pageable pageable);
}
