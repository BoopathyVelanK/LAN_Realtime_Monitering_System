package com.securesoc.repository;

import com.securesoc.entity.Laboratory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface LaboratoryRepository extends JpaRepository<Laboratory, UUID> {

    /** Used by DepartmentService to derive a per-department laboratory
     * count for GET /departments - not a stored column. */
    long countByDepartment_Id(UUID departmentId);

    /** Backs FacultyScopeService.accessibleDepartmentIds - projection of
     * distinct department IDs for a resolved set of authorized laboratory
     * IDs. Faculty department visibility is derived from this query only;
     * a department containing none of the caller's assigned laboratories
     * can never appear in the result. */
    @Query("SELECT DISTINCT l.department.id FROM Laboratory l WHERE l.id IN :labIds")
    Set<UUID> findDepartmentIdsByIdIn(@Param("labIds") Collection<UUID> labIds);
}
