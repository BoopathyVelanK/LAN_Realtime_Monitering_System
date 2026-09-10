package com.securesoc.service;

import java.util.Set;
import java.util.UUID;

/**
 * Resolves what an authenticated user is authorized to see, server-side.
 * Never trust a caller-supplied ID (path variable, query param, or request
 * body) as proof of authorization - always resolve scope through this
 * service and check the ID against it.
 *
 * ADMIN: {@link #isGlobalScope} returns true and every access check
 * returns true unconditionally - no laboratory/endpoint/student ID is ever
 * checked against a set for an Admin.
 *
 * FACULTY: scope is resolved transitively, and ONLY transitively -
 * FacultyAssignment stores laboratory grants ONLY. Endpoint and student
 * access are always derived (Laboratory -> EndpointDevice -> assigned
 * Student), never stored as a separate Faculty->Endpoint or
 * Faculty->Student mapping. This is deliberate: a redundant mapping could
 * drift out of sync with the lab assignment it was derived from and
 * silently grant access that no longer reflects reality.
 *
 * Any other role (LAB_ASSISTANT, AUDITOR, or a user with no recognized
 * role) resolves to empty scope - fail closed, not fail open - until
 * those roles' scope is explicitly designed.
 */
public interface FacultyScopeService {

    /** True for ADMIN. Callers should short-circuit on this before calling
     * any of the other methods - an Admin has no meaningful "accessible
     * laboratory IDs" set, since it is unrestricted. */
    boolean isGlobalScope(UUID userId);

    /** Laboratory IDs this user has an explicit FacultyAssignment for.
     * Empty for a non-Faculty, non-Admin user. Undefined/unused for Admin
     * - check {@link #isGlobalScope} first. */
    Set<UUID> accessibleLaboratoryIds(UUID userId);

    /** Endpoint IDs belonging to any of this user's accessible
     * laboratories. Empty for a non-Faculty, non-Admin user. */
    Set<UUID> accessibleEndpointIds(UUID userId);

    /** Student IDs assigned to an endpoint in any of this user's
     * accessible laboratories. Empty for a non-Faculty, non-Admin user. */
    Set<UUID> accessibleStudentIds(UUID userId);

    /** True if userId is ADMIN, or is FACULTY with an explicit
     * FacultyAssignment for laboratoryId. */
    boolean canAccessLaboratory(UUID userId, UUID laboratoryId);

    /** True if userId is ADMIN, or endpointId belongs to a laboratory this
     * user is assigned to. */
    boolean canAccessEndpoint(UUID userId, UUID endpointId);

    /** True if userId is ADMIN, or studentId is assigned to an endpoint in
     * a laboratory this user is assigned to. */
    boolean canAccessStudent(UUID userId, UUID studentId);

    /**
     * True only if ALL of the following hold:
     *   1. userId is ADMIN, OR userId is FACULTY assigned to laboratoryId
     *      (canAccessLaboratory), AND
     *   2. that specific FacultyAssignment row has examModeAuthorized =
     *      true.
     * Admin bypasses both checks (global access includes exam mode).
     * A FACULTY_EXAM_MODE_MANAGE-permission-holding Faculty member who is
     * NOT explicitly exam-mode-authorized for this lab must still be
     * denied - the permission alone is never sufficient. That combined
     * check (role permission AND this method) is the caller's
     * responsibility once permission-authority loading is wired up in a
     * later checkpoint; this method only answers the assignment-level
     * half of that requirement.
     */
    boolean hasExamModeAuthorization(UUID userId, UUID laboratoryId);
}
