package com.securesoc.service;

import com.securesoc.dto.DepartmentResponse;
import com.securesoc.entity.Department;
import com.securesoc.repository.DepartmentRepository;
import com.securesoc.repository.LaboratoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final FacultyScopeService facultyScopeService;

    public DepartmentService(DepartmentRepository departmentRepository,
                              LaboratoryRepository laboratoryRepository,
                              FacultyScopeService facultyScopeService) {
        this.departmentRepository = departmentRepository;
        this.laboratoryRepository = laboratoryRepository;
        this.facultyScopeService = facultyScopeService;
    }

    /**
     * Backs GET /departments. {@code callerId} is the authenticated caller
     * resolved by the controller from the JWT principal. Admin ({@link
     * FacultyScopeService#isGlobalScope}) gets exactly the previous
     * unscoped {@code findAll()} behavior. A Faculty caller gets only the
     * departments containing at least one laboratory they are assigned to,
     * via {@link FacultyScopeService#accessibleDepartmentIds} - never the
     * full department list filtered afterward in Java.
     */
    public List<DepartmentResponse> listAll(UUID callerId) {
        List<Department> departments = facultyScopeService.isGlobalScope(callerId)
            ? departmentRepository.findAll()
            : scopedDepartments(callerId);

        return departments.stream()
            .map(d -> new DepartmentResponse(
                d.getId(),
                d.getName(),
                d.getCode(),
                (int) laboratoryRepository.countByDepartment_Id(d.getId())
            ))
            .toList();
    }

    private List<Department> scopedDepartments(UUID callerId) {
        Set<UUID> departmentIds = facultyScopeService.accessibleDepartmentIds(callerId);
        return departmentIds.isEmpty() ? List.of() : departmentRepository.findAllById(departmentIds);
    }
}
