package com.securesoc.repository;

import com.securesoc.entity.RiskScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskScoreRepository extends JpaRepository<RiskScore, UUID> {

    Optional<RiskScore> findByEndpoint_Id(UUID endpointId);

    List<RiskScore> findAllByOrderByScoreDesc();

    /** Backs RiskScoreService.getAll for a Faculty caller - scoped to the
     * caller's already-resolved authorized laboratory IDs (see
     * FacultyScopeService), never an unscoped findAll() filtered in Java. */
    List<RiskScore> findByEndpoint_Lab_IdInOrderByScoreDesc(Collection<UUID> labIds);
}
