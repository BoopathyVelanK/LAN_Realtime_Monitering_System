package com.securesoc.repository;

import com.securesoc.entity.NetworkUsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NetworkUsageEventRepository extends JpaRepository<NetworkUsageEvent, UUID> {
}
