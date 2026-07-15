package com.securesoc.repository;

import com.securesoc.entity.VpnEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VpnEventRepository extends JpaRepository<VpnEvent, UUID> {
}
