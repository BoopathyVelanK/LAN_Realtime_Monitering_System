package com.securesoc.service;

import com.securesoc.dto.DepartmentResponse;
import com.securesoc.repository.DepartmentRepository;
import com.securesoc.repository.LaboratoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final LaboratoryRepository laboratoryRepository;

    public DepartmentService(DepartmentRepository departmentRepository, LaboratoryRepository laboratoryRepository) {
        this.departmentRepository = departmentRepository;
        this.laboratoryRepository = laboratoryRepository;
    }

    public List<DepartmentResponse> listAll() {
        return departmentRepository.findAll().stream()
            .map(d -> new DepartmentResponse(
                d.getId(),
                d.getName(),
                d.getCode(),
                (int) laboratoryRepository.countByDepartment_Id(d.getId())
            ))
            .toList();
    }
}
