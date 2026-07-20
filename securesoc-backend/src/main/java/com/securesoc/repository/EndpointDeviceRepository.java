package com.securesoc.repository;

import com.securesoc.entity.EndpointDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EndpointDeviceRepository extends JpaRepository<EndpointDevice, UUID> {

    Optional<EndpointDevice> findByMacAddress(String macAddress);

    Optional<EndpointDevice> findByAgentTokenHash(String agentTokenHash);

    List<EndpointDevice> findByStatusAndLastHeartbeatAtBefore(
        EndpointDevice.Status status, Instant cutoff);

    /** Used by LaboratoryService to derive a per-lab endpoint count for
     * GET /laboratories - not a stored column. */
    long countByLab_Id(UUID labId);

    /** Same as above, filtered to ONLINE - backs "active now" counts on
     * GET /laboratories without needing a separate query per status. */
    long countByLab_IdAndStatus(UUID labId, EndpointDevice.Status status);
}
