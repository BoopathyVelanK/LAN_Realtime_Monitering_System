package com.securesoc.service;

import com.securesoc.dto.EndpointSummaryResponse;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.repository.EndpointDeviceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EndpointService {

    private final EndpointDeviceRepository endpointDeviceRepository;

    public EndpointService(EndpointDeviceRepository endpointDeviceRepository) {
        this.endpointDeviceRepository = endpointDeviceRepository;
    }

    public List<EndpointSummaryResponse> listAll() {
        return endpointDeviceRepository.findAll().stream()
            .map(EndpointService::toSummary)
            .toList();
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
