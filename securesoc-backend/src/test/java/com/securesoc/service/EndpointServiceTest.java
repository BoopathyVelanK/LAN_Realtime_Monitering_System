package com.securesoc.service;

import com.securesoc.dto.EndpointSummaryResponse;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.Laboratory;
import com.securesoc.repository.EndpointDeviceRepository;
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
 * No test file previously existed for EndpointService - this covers both
 * the pre-existing listAll() mapping behavior and the new Phase 2
 * FacultyScopeService wiring (GET /endpoints must never return the full
 * fleet to a Faculty caller).
 */
@ExtendWith(MockitoExtension.class)
class EndpointServiceTest {

    @Mock
    private EndpointDeviceRepository endpointDeviceRepository;
    @Mock
    private FacultyScopeService facultyScopeService;

    private EndpointService endpointService;

    private UUID callerId;
    private UUID labId;

    @BeforeEach
    void setUp() {
        endpointService = new EndpointService(endpointDeviceRepository, facultyScopeService);
        callerId = UUID.randomUUID();
        labId = UUID.randomUUID();
    }

    private EndpointDevice deviceIn(UUID labId) {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());
        device.setHostname("lab-pc-01");
        device.setStatus(EndpointDevice.Status.ONLINE);
        if (labId != null) {
            Laboratory lab = new Laboratory();
            lab.setId(labId);
            lab.setName("Lab A");
            device.setLab(lab);
        }
        return device;
    }

    @Test
    void listAll_admin_returnsSystemWideUnfiltered() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(true);
        when(endpointDeviceRepository.findAll()).thenReturn(List.of(deviceIn(labId), deviceIn(UUID.randomUUID())));

        List<EndpointSummaryResponse> result = endpointService.listAll(callerId);

        assertEquals(2, result.size());
        verify(facultyScopeService, never()).accessibleLaboratoryIds(any());
    }

    @Test
    void listAll_facultyWithAssignedLab_returnsOnlyOwnLabEndpoints() {
        EndpointDevice ownDevice = deviceIn(labId);
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.accessibleLaboratoryIds(callerId)).thenReturn(Set.of(labId));
        when(endpointDeviceRepository.findByLab_IdIn(eq(Set.of(labId)))).thenReturn(List.of(ownDevice));

        List<EndpointSummaryResponse> result = endpointService.listAll(callerId);

        assertEquals(1, result.size());
        assertEquals(labId, result.get(0).labId());
        verify(endpointDeviceRepository, never()).findAll();
    }

    @Test
    void listAll_facultyWithNoAssignedLabs_returnsEmptyNotFullFleet() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.accessibleLaboratoryIds(callerId)).thenReturn(Set.of());

        List<EndpointSummaryResponse> result = endpointService.listAll(callerId);

        assertTrue(result.isEmpty());
        verifyNoInteractions(endpointDeviceRepository);
    }

    @Test
    void listAll_mapsFieldsCorrectly() {
        EndpointDevice device = deviceIn(labId);
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(true);
        when(endpointDeviceRepository.findAll()).thenReturn(List.of(device));

        EndpointSummaryResponse response = endpointService.listAll(callerId).get(0);

        assertEquals(device.getId(), response.id());
        assertEquals("lab-pc-01", response.hostname());
        assertEquals(labId, response.labId());
        assertEquals("Lab A", response.labName());
        assertEquals("ONLINE", response.status());
    }
}
