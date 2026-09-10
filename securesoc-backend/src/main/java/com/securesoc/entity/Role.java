package com.securesoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    /** Mirrors User.roles' existing @JoinTable convention. LAZY here (unlike
     * User.roles' EAGER) since nothing in this checkpoint's scope-resolution
     * layer reads permissions through Role - only FacultyAssignment/
     * Laboratory/EndpointDevice are walked. Revisit to EAGER (or a
     * dedicated projection) when @PreAuthorize/authorities loading is
     * wired up in the controller-authorization checkpoint. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();

    public Role(String name) {
        this.name = name;
    }
}
