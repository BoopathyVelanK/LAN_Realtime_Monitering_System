package com.securesoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Grants a FACULTY user visibility into one laboratory (and, transitively,
 * everything scoped under it: that lab's endpoints and their assigned
 * students - see FacultyScopeService, not modeled here).
 *
 * Laboratory-grain ONLY by design decision: no department-level grant
 * (deferred) and no Class/Section concept (not defined anywhere in the
 * SRS). Do not add a department_id column here without a new migration
 * and an explicit decision to support that grain.
 *
 * examModeAuthorized is a separate, explicit per-assignment grant, NOT
 * implied by the FACULTY role's permissions. A Faculty member needs BOTH
 * the EXAM_MODE_VIEW/MANAGE permission (role-level: "can this role ever
 * touch exam mode") AND examModeAuthorized = true on this specific
 * assignment (row-level: "is this Faculty member authorized for exam mode
 * in THIS lab") before exam-mode actions are allowed. See
 * FacultyScopeService.hasExamModeAuthorization.
 */
@Entity
@Table(name = "faculty_assignments")
@Getter
@Setter
@NoArgsConstructor
public class FacultyAssignment {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_user_id", nullable = false)
    private User facultyUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "laboratory_id", nullable = false)
    private Laboratory laboratory;

    @Column(name = "exam_mode_authorized", nullable = false)
    private boolean examModeAuthorized = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
