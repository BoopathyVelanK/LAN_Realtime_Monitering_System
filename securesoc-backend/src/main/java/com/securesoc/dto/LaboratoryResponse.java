package com.securesoc.dto;

import java.util.UUID;

/** GET /laboratories response shape. endpointCount/onlineEndpointCount are
 * derived (COUNT queries against endpoint_devices.lab_id), not stored
 * columns - see EndpointDeviceRepository.countByLab_Id /
 * countByLab_IdAndStatus. */
public record LaboratoryResponse(
    UUID id,
    String name,
    String code,
    UUID departmentId,
    String departmentName,
    int capacity,
    long endpointCount,
    long onlineEndpointCount
) {}
