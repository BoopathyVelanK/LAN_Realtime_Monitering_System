package com.securesoc.repository;

import com.securesoc.entity.InternetUsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InternetUsageEventRepository extends JpaRepository<InternetUsageEvent, UUID> {
}
