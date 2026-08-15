package com.securesoc.repository;

import com.securesoc.entity.DetectionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DetectionRuleRepository extends JpaRepository<DetectionRule, UUID> {

    List<DetectionRule> findByEnabledTrue();

    List<DetectionRule> findByEventSourceAndEnabledTrue(String eventSource);
}
