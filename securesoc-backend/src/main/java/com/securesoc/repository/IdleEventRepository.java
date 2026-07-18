package com.securesoc.repository;

import com.securesoc.entity.IdleEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IdleEventRepository extends JpaRepository<IdleEvent, UUID> {

    Page<IdleEvent> findAllByOrderByRecordedAtDesc(Pageable pageable);

    Page<IdleEvent> findByEndpoint_IdOrderByRecordedAtDesc(UUID endpointId, Pageable pageable);
}
