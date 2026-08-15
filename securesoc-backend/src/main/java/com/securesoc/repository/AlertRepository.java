package com.securesoc.repository;

import com.securesoc.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    Page<Alert> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Alert> findByStatusOrderByCreatedAtDesc(Alert.Status status, Pageable pageable);

    Page<Alert> findByEndpoint_IdOrderByCreatedAtDesc(UUID endpointId, Pageable pageable);
}
