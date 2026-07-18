package com.securesoc.repository;

import com.securesoc.entity.LogoutEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LogoutEventRepository extends JpaRepository<LogoutEvent, UUID> {

    Page<LogoutEvent> findAllByOrderByLogoutTimeDesc(Pageable pageable);

    Page<LogoutEvent> findByEndpoint_IdOrderByLogoutTimeDesc(UUID endpointId, Pageable pageable);
}
