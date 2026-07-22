package com.securesoc.service;
import com.securesoc.dto.LaboratoryResponse;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.repository.LaboratoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LaboratoryService {

    private final LaboratoryRepository laboratoryRepository;
    private final EndpointDeviceRepository endpointDeviceRepository;

    public LaboratoryService(LaboratoryRepository laboratoryRepository, EndpointDeviceRepository endpointDeviceRepository) {
        this.laboratoryRepository = laboratoryRepository;
        this.endpointDeviceRepository = endpointDeviceRepository;
    }
    @Transactional(readOnly = true)
    public List<LaboratoryResponse> listAll() {
        return laboratoryRepository.findAll().stream()
            .map(lab -> new LaboratoryResponse(
                lab.getId(),
                lab.getName(),
                lab.getCode(),
                lab.getDepartment() != null ? lab.getDepartment().getId() : null,
                lab.getDepartment() != null ? lab.getDepartment().getName() : null,
                lab.getCapacity(),
                endpointDeviceRepository.countByLab_Id(lab.getId()),
                endpointDeviceRepository.countByLab_IdAndStatus(lab.getId(), EndpointDevice.Status.ONLINE)
            ))
            .toList();
    }
}
