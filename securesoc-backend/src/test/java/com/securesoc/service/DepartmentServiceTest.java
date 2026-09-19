package com.securesoc.service;

import com.securesoc.dto.DepartmentResponse;
import com.securesoc.entity.Department;
import com.securesoc.repository.DepartmentRepository;
import com.securesoc.repository.LaboratoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * No test file previously existed for DepartmentService - this covers
 * both the pre-existing listAll() mapping behavior and the REST scope
 * enforcement checkpoint's FacultyScopeService wiring (GET /departments
 * must never return every department to a Faculty caller).
 */
@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private LaboratoryRepository laboratoryRepository;
    @Mock
    private FacultyScopeService facultyScopeService;

    private DepartmentService departmentService;

    private UUID callerId;
    private UUID deptA;
    private UUID deptB;

    @BeforeEach
    void setUp() {
        departmentService = new DepartmentService(departmentRepository, laboratoryRepository, facultyScopeService);
        callerId = UUID.randomUUID();
        deptA = UUID.randomUUID();
        deptB = UUID.randomUUID();
    }

    private Department department(UUID id) {
        Department d = new Department();
        d.setId(id);
        d.setName("Department " + id.toString().substring(0, 4));
        d.setCode("D-" + id.toString().substring(0, 4));
        return d;
    }

    @Test
    void listAll_admin_returnsSystemWideUnfiltered() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(true);
        when(departmentRepository.findAll()).thenReturn(List.of(department(deptA), department(deptB)));

        List<DepartmentResponse> result = departmentService.listAll(callerId);

        assertEquals(2, result.size());
        verify(facultyScopeService, never()).accessibleDepartmentIds(any());
    }

    @Test
    void listAll_facultyWithAssignedLab_returnsOnlyOwnDepartment() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.accessibleDepartmentIds(callerId)).thenReturn(Set.of(deptA));
        when(departmentRepository.findAllById(eq(Set.of(deptA)))).thenReturn(List.of(department(deptA)));

        List<DepartmentResponse> result = departmentService.listAll(callerId);

        assertEquals(1, result.size());
        assertEquals(deptA, result.get(0).id());
        verify(departmentRepository, never()).findAll();
    }

    @Test
    void listAll_facultyWithNoAssignedLabs_returnsEmptyNotEveryDepartment() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.accessibleDepartmentIds(callerId)).thenReturn(Set.of());

        List<DepartmentResponse> result = departmentService.listAll(callerId);

        assertTrue(result.isEmpty());
        verifyNoInteractions(departmentRepository);
    }

    @Test
    void listAll_facultyAssignedInDeptAOnly_neverReceivesDeptB() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.accessibleDepartmentIds(callerId)).thenReturn(Set.of(deptA));
        when(departmentRepository.findAllById(eq(Set.of(deptA)))).thenReturn(List.of(department(deptA)));

        List<DepartmentResponse> result = departmentService.listAll(callerId);

        assertTrue(result.stream().noneMatch(r -> r.id().equals(deptB)));
    }

    @Test
    void listAll_mapsFieldsCorrectly() {
        Department dept = department(deptA);
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(true);
        when(departmentRepository.findAll()).thenReturn(List.of(dept));
        when(laboratoryRepository.countByDepartment_Id(deptA)).thenReturn(3L);

        DepartmentResponse response = departmentService.listAll(callerId).get(0);

        assertEquals(dept.getId(), response.id());
        assertEquals(dept.getName(), response.name());
        assertEquals(dept.getCode(), response.code());
        assertEquals(3, response.laboratoryCount());
    }
}
