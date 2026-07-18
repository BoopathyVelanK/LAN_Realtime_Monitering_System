package com.securesoc.repository;

import com.securesoc.entity.NetworkUsageEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NetworkUsageEventRepository extends JpaRepository<NetworkUsageEvent, UUID> {

    Page<NetworkUsageEvent> findAllByOrderByRecordedAtDesc(Pageable pageable);

    Page<NetworkUsageEvent> findByEndpoint_IdOrderByRecordedAtDesc(UUID endpointId, Pageable pageable);
}
