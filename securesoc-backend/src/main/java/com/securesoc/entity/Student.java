package com.securesoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A monitored student, NOT a system principal - students never authenticate
 * into SecureSOC. Deliberately separate from {@link User} (see RBAC design
 * proposal): folding this into User+role would require nonsensical auth
 * columns (password_hash, lockout, etc.) on a non-authenticating subject,
 * and would pollute permission checks with a role that can never hold any
 * permission by definition.
 */
@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
public class Student {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "student_code", nullable = false, unique = true, length = 50)
    private String studentCode;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
