package com.securesoc.repository;

import com.securesoc.entity.UsbEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UsbEventRepository extends JpaRepository<UsbEvent, UUID> {

    Page<UsbEvent> findAllByOrderByEventTimeDesc(Pageable pageable);

    Page<UsbEvent> findByEndpoint_IdOrderByEventTimeDesc(UUID endpointId, Pageable pageable);
}
