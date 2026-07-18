package com.securesoc.repository;

import com.securesoc.entity.RunningAppSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RunningAppSnapshotRepository extends JpaRepository<RunningAppSnapshot, UUID> {

    Page<RunningAppSnapshot> findAllByOrderByCapturedAtDesc(Pageable pageable);

    Page<RunningAppSnapshot> findByEndpoint_IdOrderByCapturedAtDesc(UUID endpointId, Pageable pageable);
}
