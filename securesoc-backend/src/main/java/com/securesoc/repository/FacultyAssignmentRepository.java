package com.securesoc.repository;

import com.securesoc.entity.FacultyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

public interface FacultyAssignmentRepository extends JpaRepository<FacultyAssignment, UUID> {

    /** Backs FacultyScopeService.accessibleLaboratoryIds - a projection
     * query so the DB returns only the IDs actually needed, rather than
     * loading full FacultyAssignment/Laboratory rows to be mapped in Java. */
    @Query("SELECT fa.laboratory.id FROM FacultyAssignment fa WHERE fa.facultyUser.id = :facultyUserId")
    Set<UUID> findLaboratoryIdsByFacultyUser_Id(@Param("facultyUserId") UUID facultyUserId);

    /** Backs FacultyScopeService.canAccessLaboratory - a single existence
     * check, no row ever loaded into memory either way. */
    boolean existsByFacultyUser_IdAndLaboratory_Id(UUID facultyUserId, UUID laboratoryId);

    /** Backs FacultyScopeService.hasExamModeAuthorization. Deliberately
     * requires BOTH the faculty/lab match AND examModeAuthorized = true
     * in one query - "assigned to this lab" and "exam-mode authorized for
     * this lab" are two different facts and must not be conflated. */
    boolean existsByFacultyUser_IdAndLaboratory_IdAndExamModeAuthorizedTrue(UUID facultyUserId, UUID laboratoryId);
}
