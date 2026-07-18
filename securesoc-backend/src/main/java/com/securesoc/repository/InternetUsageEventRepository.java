package com.securesoc.repository;

import com.securesoc.entity.InternetUsageEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InternetUsageEventRepository extends JpaRepository<InternetUsageEvent, UUID> {

    Page<InternetUsageEvent> findAllByOrderByRecordedAtDesc(Pageable pageable);

    Page<InternetUsageEvent> findByEndpoint_IdOrderByRecordedAtDesc(UUID endpointId, Pageable pageable);
}
