package com.securesoc.service;

import com.securesoc.dto.EndpointSummaryResponse;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.repository.EndpointDeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class EndpointService {

    private final EndpointDeviceRepository endpointDeviceRepository;
    private final FacultyScopeService facultyScopeService;

    public EndpointService(
        EndpointDeviceRepository endpointDeviceRepository,
        FacultyScopeService facultyScopeService
    ) {
        this.endpointDeviceRepository = endpointDeviceRepository;
        this.facultyScopeService = facultyScopeService;
    }

    /**
     * Backs GET /endpoints. {@code callerId} is the authenticated caller
     * resolved by the controller from the JWT principal. Admin ({@link
     * FacultyScopeService#isGlobalScope}) gets exactly the previous
     * unscoped {@code findAll()} behavior. A Faculty caller gets only the
     * endpoints in their assigned labs, via a scoped repository query
     * ({@link com.securesoc.repository.EndpointDeviceRepository#findByLab_IdIn})
     * - never the full fleet filtered afterward in Java.
     */
    @Transactional(readOnly = true)
    public List<EndpointSummaryResponse> listAll(UUID callerId) {
        List<EndpointDevice> devices = facultyScopeService.isGlobalScope(callerId)
            ? endpointDeviceRepository.findAll()
            : scopedEndpoints(callerId);
        return devices.stream()
            .map(EndpointService::toSummary)
            .toList();
    }

    private List<EndpointDevice> scopedEndpoints(UUID callerId) {
        Set<UUID> labIds = facultyScopeService.accessibleLaboratoryIds(callerId);
        return labIds.isEmpty() ? List.of() : endpointDeviceRepository.findByLab_IdIn(labIds);
    }

    static EndpointSummaryResponse toSummary(EndpointDevice d) {
        return new EndpointSummaryResponse(
            d.getId(),
            d.getHostname(),
            d.getMacAddress(),
            d.getIpAddress(),
            d.getLab() != null ? d.getLab().getId() : null,
            d.getLab() != null ? d.getLab().getName() : null,
            d.getStatus().name(),
            d.getLastHeartbeatAt(),
            d.getOsName(),
            d.getOsVersion(),
            d.getCpuInfo(),
            d.getRamMb(),
            d.getDiskGb(),
            d.getAgentVersion()
        );
    }
}
