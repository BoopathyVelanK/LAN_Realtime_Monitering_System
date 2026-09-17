package com.securesoc.service;

import com.securesoc.dto.LaboratoryResponse;
import com.securesoc.entity.Department;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.Laboratory;
import com.securesoc.repository.EndpointDeviceRepository;
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
 * No test file previously existed for LaboratoryService - this covers
 * both the pre-existing listAll() mapping behavior and the REST scope
 * enforcement checkpoint's FacultyScopeService wiring (GET /laboratories
 * must never return every laboratory to a Faculty caller).
 */
@ExtendWith(MockitoExtension.class)
class LaboratoryServiceTest {

    @Mock
    private LaboratoryRepository laboratoryRepository;
    @Mock
    private EndpointDeviceRepository endpointDeviceRepository;
    @Mock
    private FacultyScopeService facultyScopeService;

    private LaboratoryService laboratoryService;

    private UUID callerId;
    private UUID labA;
    private UUID labB;

    @BeforeEach
    void setUp() {
        laboratoryService = new LaboratoryService(laboratoryRepository, endpointDeviceRepository, facultyScopeService);
        callerId = UUID.randomUUID();
        labA = UUID.randomUUID();
        labB = UUID.randomUUID();
    }

    private Laboratory lab(UUID id) {
        Laboratory l = new Laboratory();
        l.setId(id);
        l.setName("Lab " + id.toString().substring(0, 4));
        l.setCode("L-" + id.toString().substring(0, 4));
        l.setCapacity(30);
        Department dept = new Department();
        dept.setId(UUID.randomUUID());
        dept.setName("Dept");
        dept.setCode("DPT");
        l.setDepartment(dept);
        return l;
    }

    @Test
    void listAll_admin_returnsSystemWideUnfiltered() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(true);
        when(laboratoryRepository.findAll()).thenReturn(List.of(lab(labA), lab(labB)));

        List<LaboratoryResponse> result = laboratoryService.listAll(callerId);

        assertEquals(2, result.size());
        verify(facultyScopeService, never()).accessibleLaboratoryIds(any());
    }

    @Test
    void listAll_facultyWithAssignedLab_returnsOnlyOwnLaboratory() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.accessibleLaboratoryIds(callerId)).thenReturn(Set.of(labA));
        when(laboratoryRepository.findAllById(eq(Set.of(labA)))).thenReturn(List.of(lab(labA)));

        List<LaboratoryResponse> result = laboratoryService.listAll(callerId);

        assertEquals(1, result.size());
        assertEquals(labA, result.get(0).id());
        verify(laboratoryRepository, never()).findAll();
    }

    @Test
    void listAll_facultyWithNoAssignedLabs_returnsEmptyNotEveryLaboratory() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.accessibleLaboratoryIds(callerId)).thenReturn(Set.of());

        List<LaboratoryResponse> result = laboratoryService.listAll(callerId);

        assertTrue(result.isEmpty());
        verifyNoInteractions(laboratoryRepository, endpointDeviceRepository);
    }

    @Test
    void listAll_facultyAssignedToLabAOnly_neverReceivesLabB() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.accessibleLaboratoryIds(callerId)).thenReturn(Set.of(labA));
        when(laboratoryRepository.findAllById(eq(Set.of(labA)))).thenReturn(List.of(lab(labA)));

        List<LaboratoryResponse> result = laboratoryService.listAll(callerId);

        assertTrue(result.stream().noneMatch(r -> r.id().equals(labB)));
    }

    @Test
    void listAll_mapsFieldsCorrectly() {
        Laboratory laboratory = lab(labA);
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(true);
        when(laboratoryRepository.findAll()).thenReturn(List.of(laboratory));
        when(endpointDeviceRepository.countByLab_Id(labA)).thenReturn(5L);
        when(endpointDeviceRepository.countByLab_IdAndStatus(labA, EndpointDevice.Status.ONLINE)).thenReturn(2L);

        LaboratoryResponse response = laboratoryService.listAll(callerId).get(0);

        assertEquals(laboratory.getId(), response.id());
        assertEquals(laboratory.getName(), response.name());
        assertEquals(laboratory.getCode(), response.code());
        assertEquals(laboratory.getDepartment().getId(), response.departmentId());
        assertEquals(5L, response.endpointCount());
        assertEquals(2L, response.onlineEndpointCount());
    }
}
