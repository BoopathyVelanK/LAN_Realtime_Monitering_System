package com.securesoc.repository;

import com.securesoc.entity.LoginEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoginEventRepository extends JpaRepository<LoginEvent, UUID> {

    Page<LoginEvent> findAllByOrderByLoginTimeDesc(Pageable pageable);

    Page<LoginEvent> findByEndpoint_IdOrderByLoginTimeDesc(UUID endpointId, Pageable pageable);
}
