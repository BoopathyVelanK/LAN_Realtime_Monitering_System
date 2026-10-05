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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final EndpointDeviceRepository endpointDeviceRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final AgentProperties agentProperties;
    private final EndpointEventPublisher endpointEventPublisher;

    public AgentService(
        EndpointDeviceRepository endpointDeviceRepository,
        LaboratoryRepository laboratoryRepository,
        AgentProperties agentProperties,
        EndpointEventPublisher endpointEventPublisher
    ) {
        this.endpointDeviceRepository = endpointDeviceRepository;
        this.laboratoryRepository = laboratoryRepository;
        this.agentProperties = agentProperties;
        this.endpointEventPublisher = endpointEventPublisher;
    }

    /**
     * Registration is idempotent on the endpoint's stable identity: an agent
     * that already has a valid local state file never calls this (see
     * agent.py's AgentState), but a machine re-registering after its state
     * file was lost gets a FRESH token issued for the SAME device record
     * rather than a duplicate row - the old token is implicitly invalidated
     * by being overwritten.
     *
     * Lookup order (see {@link #findExistingDevice}): deviceId (Windows
     * MachineGuid) when the agent sent one, then MAC address (legacy agents,
     * and legacy rows that predate device_id), otherwise a new row. MAC
     * address is stored and updated as metadata but is no longer the only
     * way to recognise a device.
     */
    @Transactional
    public AgentRegisterResponse register(AgentRegisterRequest request, String presentedSecret) {
        if (presentedSecret == null || !presentedSecret.equals(agentProperties.registrationSecret())) {
            throw new UnauthorizedException("Invalid agent registration secret");
        }

        String deviceId = normalizeDeviceId(request.deviceId());

        EndpointDevice device = findExistingDevice(deviceId, request.macAddress())
            .orElseGet(EndpointDevice::new);

        device.setHostname(request.hostname());
        applyMacAddress(device, request.macAddress());
        if (deviceId != null && device.getDeviceId() == null) {
            // New device, or a legacy row (found via MAC) adopting its stable identity.
            device.setDeviceId(deviceId);
        }
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

    /** deviceId first (when sent), then MAC. Falling back to MAC even when a
     * deviceId was sent is what lets a legacy row (device_id NULL) be adopted
     * instead of duplicated. If that MAC row already belongs to a DIFFERENT
     * deviceId it is a different machine claiming the same MAC; mac_address is
     * still UNIQUE, so a second row cannot be created - reject rather than
     * hijack the other device's row or fail later with a constraint error. */
    private Optional<EndpointDevice> findExistingDevice(String deviceId, String macAddress) {
        if (deviceId != null) {
            Optional<EndpointDevice> byDeviceId = endpointDeviceRepository.findByDeviceId(deviceId);
            if (byDeviceId.isPresent()) {
                return byDeviceId;
            }
        }

        Optional<EndpointDevice> byMac = endpointDeviceRepository.findByMacAddress(macAddress);
        if (deviceId != null && byMac.isPresent()
            && byMac.get().getDeviceId() != null
            && !deviceId.equals(byMac.get().getDeviceId())) {
            throw new IllegalArgumentException(
                "MAC address is already registered to a different device identity");
        }
        return byMac;
    }

    /** Updates the stored MAC on an existing device unless another row
     * already owns the new one (mac_address is UNIQUE). MAC is metadata once
     * a deviceId identifies the row, so keeping the previous value is safer
     * than failing the whole registration; no deviceId is ever logged. */
    private void applyMacAddress(EndpointDevice device, String macAddress) {
        if (macAddress.equals(device.getMacAddress())) {
            return;
        }
        if (device.getId() != null) {
            Optional<EndpointDevice> owner = endpointDeviceRepository.findByMacAddress(macAddress);
            if (owner.isPresent() && !owner.get().getId().equals(device.getId())) {
                log.warn("Keeping existing MAC for endpoint {}: the reported MAC is already registered to another endpoint.",
                    device.getId());
                return;
            }
        }
        device.setMacAddress(macAddress);
    }

    private static String normalizeDeviceId(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    /** Caller (AgentController) has already authenticated the device via
     * AgentTokenAuthFilter by this point - the EndpointDevice principal is
     * passed straight in rather than re-resolved from the token.
     *
     * Phase 6: publishes an EndpointStatusEvent (see EndpointEventPublisher)
     * only when the device actually transitions - most heartbeats arrive
     * while the device is already ONLINE, and re-publishing on every single
     * one of those (every ~15s per device by default) would flood the
     * topic for no frontend benefit. */
    @Transactional
    public AgentHeartbeatResponse heartbeat(EndpointDevice device, AgentHeartbeatRequest request) {
        EndpointDevice.Status previousStatus = device.getStatus();

        device.setStatus(EndpointDevice.Status.ONLINE);
        device.setLastHeartbeatAt(Instant.now());
        if (request.ipAddress() != null && !request.ipAddress().isBlank()) {
            device.setIpAddress(request.ipAddress());
        }

        EndpointDevice saved = endpointDeviceRepository.save(device);

        if (previousStatus != EndpointDevice.Status.ONLINE) {
            endpointEventPublisher.publishStatusChange(saved);
        }

        return new AgentHeartbeatResponse("ONLINE");
    }
}
