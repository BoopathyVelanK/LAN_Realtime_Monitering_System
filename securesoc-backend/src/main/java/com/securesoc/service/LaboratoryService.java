package com.securesoc.service;

import com.securesoc.dto.LaboratoryResponse;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.Laboratory;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.repository.LaboratoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class LaboratoryService {

    private final LaboratoryRepository laboratoryRepository;
    private final EndpointDeviceRepository endpointDeviceRepository;
    private final FacultyScopeService facultyScopeService;

    public LaboratoryService(LaboratoryRepository laboratoryRepository,
                              EndpointDeviceRepository endpointDeviceRepository,
                              FacultyScopeService facultyScopeService) {
        this.laboratoryRepository = laboratoryRepository;
        this.endpointDeviceRepository = endpointDeviceRepository;
        this.facultyScopeService = facultyScopeService;
    }

    /**
     * Backs GET /laboratories. {@code callerId} is the authenticated
     * caller resolved by the controller from the JWT principal. Admin
     * ({@link FacultyScopeService#isGlobalScope}) gets exactly the
     * previous unscoped {@code findAll()} behavior. A Faculty caller gets
     * only the laboratories returned by {@link
     * FacultyScopeService#accessibleLaboratoryIds} - never the full
     * laboratory list filtered afterward in Java.
     */
    @Transactional(readOnly = true)
    public List<LaboratoryResponse> listAll(UUID callerId) {
        List<Laboratory> labs = facultyScopeService.isGlobalScope(callerId)
            ? laboratoryRepository.findAll()
            : scopedLaboratories(callerId);

        return labs.stream()
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

    private List<Laboratory> scopedLaboratories(UUID callerId) {
        Set<UUID> labIds = facultyScopeService.accessibleLaboratoryIds(callerId);
        return labIds.isEmpty() ? List.of() : laboratoryRepository.findAllById(labIds);
    }
}
