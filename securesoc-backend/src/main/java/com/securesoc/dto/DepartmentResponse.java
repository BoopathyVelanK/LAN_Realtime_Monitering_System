package com.securesoc.dto;

import java.util.UUID;

/** GET /departments response shape. laboratoryCount is derived (a COUNT
 * query against laboratories.department_id), not a stored column. */
public record DepartmentResponse(
    UUID id,
    String name,
    String code,
    int laboratoryCount
) {}
