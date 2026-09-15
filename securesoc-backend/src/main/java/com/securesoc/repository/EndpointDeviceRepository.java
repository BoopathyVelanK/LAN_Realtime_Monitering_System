package com.securesoc.repository;

import com.securesoc.entity.EndpointDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface EndpointDeviceRepository extends JpaRepository<EndpointDevice, UUID> {

    Optional<EndpointDevice> findByMacAddress(String macAddress);

    Optional<EndpointDevice> findByAgentTokenHash(String agentTokenHash);

    List<EndpointDevice> findByStatusAndLastHeartbeatAtBefore(
        EndpointDevice.Status status, Instant cutoff);

    /** Used by LaboratoryService to derive a per-lab endpoint count for
     * GET /laboratories - not a stored column. */
    long countByLab_Id(UUID labId);

    /** Same as above, filtered to ONLINE - backs "active now" counts on
     * GET /laboratories without needing a separate query per status. */
    long countByLab_IdAndStatus(UUID labId, EndpointDevice.Status status);

    // --- Faculty scope resolution (FacultyScopeService) -----------------
    // Every method below is a projection or existence check constrained
    // by an already-resolved set of authorized laboratory IDs - none of
    // them load unscoped rows to filter in Java afterward.

    /** Backs FacultyScopeService.accessibleEndpointIds. */
    @Query("SELECT e.id FROM EndpointDevice e WHERE e.lab.id IN :labIds")
    Set<UUID> findIdsByLab_IdIn(@Param("labIds") Collection<UUID> labIds);

    /** Backs EndpointService.listAll for a Faculty caller (GET /endpoints) -
     * full-entity variant of {@link #findIdsByLab_IdIn}, needed because the
     * endpoint listing response requires the whole EndpointSummaryResponse
     * shape, not just IDs. Same scoping guarantee: an endpoint outside the
     * caller's assigned labs can never appear here. */
    List<EndpointDevice> findByLab_IdIn(Collection<UUID> labIds);

    /** Backs FacultyScopeService.canAccessEndpoint - single existence
     * check against the caller's already-resolved allowed labs, so an
     * endpoint ID for a lab the caller isn't assigned to can never
     * resolve true, however it was supplied (path var, query param, or
     * request body). */
    boolean existsByIdAndLab_IdIn(UUID id, Collection<UUID> labIds);

    /** Backs FacultyScopeService.accessibleStudentIds - the transitive
     * Laboratory -> EndpointDevice -> assignedStudent walk described in
     * FacultyAssignment's Javadoc. Students are never linked to a lab or
     * faculty directly; this is the one query that performs that
     * derivation, so it is not duplicated anywhere else. */
    @Query("SELECT e.assignedStudent.id FROM EndpointDevice e " +
           "WHERE e.lab.id IN :labIds AND e.assignedStudent IS NOT NULL")
    Set<UUID> findAssignedStudentIdsByLab_IdIn(@Param("labIds") Collection<UUID> labIds);

    /** Backs FacultyScopeService.canAccessStudent - true only if some
     * endpoint in one of the caller's allowed labs has this exact student
     * assigned. A studentId for a student attached to an endpoint in a
     * lab the caller isn't assigned to can never resolve true. */
    boolean existsByAssignedStudent_IdAndLab_IdIn(UUID studentId, Collection<UUID> labIds);
}
