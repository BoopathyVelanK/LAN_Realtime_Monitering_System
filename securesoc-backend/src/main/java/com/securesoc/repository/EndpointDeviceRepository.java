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
}
