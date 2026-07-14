package com.securesoc.service;

import com.securesoc.config.AgentProperties;
import com.securesoc.dto.agent.AgentHeartbeatRequest;
import com.securesoc.dto.agent.AgentHeartbeatResponse;
import com.securesoc.dto.agent.AgentRegisterRequest;
import com.securesoc.dto.agent.AgentRegisterResponse;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.exception.UnauthorizedException;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.repository.LaboratoryRepository;
import com.securesoc.security.TokenHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AgentService {

    private final EndpointDeviceRepository endpointDeviceRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final AgentProperties agentProperties;

    public AgentService(
        EndpointDeviceRepository endpointDeviceRepository,
        LaboratoryRepository laboratoryRepository,
        AgentProperties agentProperties
    ) {
        this.endpointDeviceRepository = endpointDeviceRepository;
        this.laboratoryRepository = laboratoryRepository;
        this.agentProperties = agentProperties;
    }

    /**
     * Registration is idempotent on MAC address: an agent that already has
     * a valid local state file never calls this (see agent.py's
     * AgentState), but a machine re-registering after its state file was
     * lost gets a FRESH token issued for the SAME device record rather
     * than a duplicate row - the old token is implicitly invalidated by
     * being overwritten.
     */
    @Transactional
    public AgentRegisterResponse register(AgentRegisterRequest request, String presentedSecret) {
        if (presentedSecret == null || !presentedSecret.equals(agentProperties.registrationSecret())) {
            throw new UnauthorizedException("Invalid agent registration secret");
        }

        EndpointDevice device = endpointDeviceRepository.findByMacAddress(request.macAddress())
            .orElseGet(EndpointDevice::new);

        device.setHostname(request.hostname());
        device.setMacAddress(request.macAddress());
        device.setIpAddress(request.ipAddress());
        device.setOsName(request.osName());
        device.setOsVersion(request.osVersion());
        device.setCpuInfo(request.cpuInfo());
        device.setRamMb(request.ramMb());
        device.setDiskGb(request.diskGb());
        device.setAgentVersion(request.agentVersion());
        device.setStatus(EndpointDevice.Status.OFFLINE); // becomes ONLINE on first heartbeat

        if (request.labId() != null && !request.labId().isBlank()) {
            try {
                UUID labId = UUID.fromString(request.labId());
                laboratoryRepository.findById(labId).ifPresent(device::setLab);
            } catch (IllegalArgumentException ignored) {
                // Not a valid UUID (or unassigned) - device stays unassigned;
                // an admin can assign it a lab later from the Inventory page.
            }
        }

        String rawToken = TokenHasher.generateOpaqueToken();
        device.setAgentTokenHash(TokenHasher.sha256Hex(rawToken));

        EndpointDevice saved = endpointDeviceRepository.save(device);

        String message = device.getLab() == null
            ? "Registered. Endpoint is unassigned to a lab - an admin can assign it from Inventory."
            : "Registered and assigned to " + device.getLab().getName() + ".";

        return new AgentRegisterResponse(saved.getId(), rawToken, "OFFLINE", message);
    }

    /** Caller (AgentController) has already authenticated the device via
     * AgentTokenAuthFilter by this point - the EndpointDevice principal is
     * passed straight in rather than re-resolved from the token. */
    @Transactional
    public AgentHeartbeatResponse heartbeat(EndpointDevice device, AgentHeartbeatRequest request) {
        device.setStatus(EndpointDevice.Status.ONLINE);
        device.setLastHeartbeatAt(Instant.now());
        if (request.ipAddress() != null && !request.ipAddress().isBlank()) {
            device.setIpAddress(request.ipAddress());
        }
        endpointDeviceRepository.save(device);
        return new AgentHeartbeatResponse("ONLINE");
    }
}
