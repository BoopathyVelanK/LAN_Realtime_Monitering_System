package com.securesoc.repository;

import com.securesoc.entity.VpnEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VpnEventRepository extends JpaRepository<VpnEvent, UUID> {

    Page<VpnEvent> findAllByOrderByDetectedAtDesc(Pageable pageable);

    Page<VpnEvent> findByEndpoint_IdOrderByDetectedAtDesc(UUID endpointId, Pageable pageable);
}
