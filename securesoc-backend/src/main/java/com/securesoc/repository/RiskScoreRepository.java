package com.securesoc.repository;

import com.securesoc.entity.RiskScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskScoreRepository extends JpaRepository<RiskScore, UUID> {

    Optional<RiskScore> findByEndpoint_Id(UUID endpointId);

    List<RiskScore> findAllByOrderByScoreDesc();
}
