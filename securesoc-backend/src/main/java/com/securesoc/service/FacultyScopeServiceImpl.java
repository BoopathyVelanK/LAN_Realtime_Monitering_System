package com.securesoc.service;

import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.repository.FacultyAssignmentRepository;
import com.securesoc.repository.LaboratoryRepository;
import com.securesoc.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class FacultyScopeServiceImpl implements FacultyScopeService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;
    private final FacultyAssignmentRepository facultyAssignmentRepository;
    private final EndpointDeviceRepository endpointDeviceRepository;
    private final LaboratoryRepository laboratoryRepository;

    public FacultyScopeServiceImpl(UserRepository userRepository,
                                    FacultyAssignmentRepository facultyAssignmentRepository,
                                    EndpointDeviceRepository endpointDeviceRepository,
                                    LaboratoryRepository laboratoryRepository) {
        this.userRepository = userRepository;
        this.facultyAssignmentRepository = facultyAssignmentRepository;
        this.endpointDeviceRepository = endpointDeviceRepository;
        this.laboratoryRepository = laboratoryRepository;
    }

    @Override
    public boolean isGlobalScope(UUID userId) {
        return userRepository.findById(userId)
            .map(u -> u.getRoles().stream().anyMatch(r -> ADMIN_ROLE.equals(r.getName())))
            .orElse(false);
    }

    @Override
    public Set<UUID> accessibleLaboratoryIds(UUID userId) {
        if (isGlobalScope(userId)) {
            return Set.of();
        }
        return facultyAssignmentRepository.findLaboratoryIdsByFacultyUser_Id(userId);
    }

    @Override
    public Set<UUID> accessibleEndpointIds(UUID userId) {
        if (isGlobalScope(userId)) {
            return Set.of();
        }
        Set<UUID> labIds = accessibleLaboratoryIds(userId);
        if (labIds.isEmpty()) {
            return Set.of();
        }
        return endpointDeviceRepository.findIdsByLab_IdIn(labIds);
    }

    @Override
    public Set<UUID> accessibleStudentIds(UUID userId) {
        if (isGlobalScope(userId)) {
            return Set.of();
        }
        Set<UUID> labIds = accessibleLaboratoryIds(userId);
        if (labIds.isEmpty()) {
            return Set.of();
        }
        return endpointDeviceRepository.findAssignedStudentIdsByLab_IdIn(labIds);
    }

    @Override
    public Set<UUID> accessibleDepartmentIds(UUID userId) {
        if (isGlobalScope(userId)) {
            return Set.of();
        }
        Set<UUID> labIds = accessibleLaboratoryIds(userId);
        if (labIds.isEmpty()) {
            return Set.of();
        }
        return laboratoryRepository.findDepartmentIdsByIdIn(labIds);
    }

    @Override
    public boolean canAccessLaboratory(UUID userId, UUID laboratoryId) {
        if (isGlobalScope(userId)) {
            return true;
        }
        return facultyAssignmentRepository.existsByFacultyUser_IdAndLaboratory_Id(userId, laboratoryId);
    }

    @Override
    public boolean canAccessEndpoint(UUID userId, UUID endpointId) {
        if (isGlobalScope(userId)) {
            return true;
        }
        Set<UUID> labIds = accessibleLaboratoryIds(userId);
        if (labIds.isEmpty()) {
            return false;
        }
        return endpointDeviceRepository.existsByIdAndLab_IdIn(endpointId, labIds);
    }

    @Override
    public boolean canAccessStudent(UUID userId, UUID studentId) {
        if (isGlobalScope(userId)) {
            return true;
        }
        Set<UUID> labIds = accessibleLaboratoryIds(userId);
        if (labIds.isEmpty()) {
            return false;
        }
        return endpointDeviceRepository.existsByAssignedStudent_IdAndLab_IdIn(studentId, labIds);
    }

    @Override
    public boolean hasExamModeAuthorization(UUID userId, UUID laboratoryId) {
        if (isGlobalScope(userId)) {
            return true;
        }
        return facultyAssignmentRepository
            .existsByFacultyUser_IdAndLaboratory_IdAndExamModeAuthorizedTrue(userId, laboratoryId);
    }
}
