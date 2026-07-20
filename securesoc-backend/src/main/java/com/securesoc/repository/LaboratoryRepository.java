package com.securesoc.repository;

import com.securesoc.entity.Laboratory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LaboratoryRepository extends JpaRepository<Laboratory, UUID> {

    /** Used by DepartmentService to derive a per-department laboratory
     * count for GET /departments - not a stored column. */
    long countByDepartment_Id(UUID departmentId);
}
