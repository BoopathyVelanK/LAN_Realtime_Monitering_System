package com.securesoc.service;

import com.securesoc.entity.Role;
import com.securesoc.entity.User;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.repository.FacultyAssignmentRepository;
import com.securesoc.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the scope-resolution layer only - no controllers, no
 * @PreAuthorize, no WebSocket. Repositories are mocked; the DB-level
 * projection/existence queries themselves (findLaboratoryIdsByFacultyUser_Id,
 * existsByIdAndLab_IdIn, etc.) are Spring Data derived/@Query methods with
 * no custom logic to unit-test - their correctness is a JPQL/SQL concern,
 * exercised for real once controller wiring reaches an integration test in
 * the next checkpoint.
 */
@ExtendWith(MockitoExtension.class)
class FacultyScopeServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FacultyAssignmentRepository facultyAssignmentRepository;
    @Mock
    private EndpointDeviceRepository endpointDeviceRepository;

    private FacultyScopeServiceImpl scopeService;

    private UUID adminId;
    private UUID facultyAId;
    private UUID facultyBId;
    private UUID labX;
    private UUID labY;
    private UUID endpointInLabX;
    private UUID endpointInLabY;
    private UUID studentOnEndpointInLabX;
    private UUID studentOnEndpointInLabY;

    @BeforeEach
    void setUp() {
        scopeService = new FacultyScopeServiceImpl(userRepository, facultyAssignmentRepository, endpointDeviceRepository);

        adminId = UUID.randomUUID();
        facultyAId = UUID.randomUUID();
        facultyBId = UUID.randomUUID();
        labX = UUID.randomUUID();
        labY = UUID.randomUUID();
        endpointInLabX = UUID.randomUUID();
        endpointInLabY = UUID.randomUUID();
        studentOnEndpointInLabX = UUID.randomUUID();
        studentOnEndpointInLabY = UUID.randomUUID();
    }

    private User userWithRole(String roleName) {
        User user = new User();
        Role role = new Role(roleName);
        user.setRoles(Set.of(role));
        return user;
    }

    // Faculty A is assigned to labX only. Faculty B is assigned to labY
    // only - representing "another Faculty member's scope" throughout.
    private void stubFacultyAAssignedToLabXOnly() {
        when(userRepository.findById(facultyAId)).thenReturn(Optional.of(userWithRole("FACULTY")));
        when(facultyAssignmentRepository.findLaboratoryIdsByFacultyUser_Id(facultyAId)).thenReturn(Set.of(labX));
    }

    @Nested
    class Admin {

        @Test
        void isGlobalScopeTrue() {
            when(userRepository.findById(adminId)).thenReturn(Optional.of(userWithRole("ADMIN")));
            assertThat(scopeService.isGlobalScope(adminId)).isTrue();
        }

        @Test
        void canAccessAnyLaboratoryWithoutConsultingAssignments() {
            when(userRepository.findById(adminId)).thenReturn(Optional.of(userWithRole("ADMIN")));

            assertThat(scopeService.canAccessLaboratory(adminId, labX)).isTrue();
            assertThat(scopeService.canAccessLaboratory(adminId, labY)).isTrue();

            // Admin's global access must never depend on any assignment
            // row existing - proves this isn't accidentally "allowed
            // because assignment lookup returned empty/true by mistake".
            verify(facultyAssignmentRepository, never()).existsByFacultyUser_IdAndLaboratory_Id(any(), any());
        }

        @Test
        void canAccessAnyEndpointAndStudent() {
            when(userRepository.findById(adminId)).thenReturn(Optional.of(userWithRole("ADMIN")));

            assertThat(scopeService.canAccessEndpoint(adminId, endpointInLabX)).isTrue();
            assertThat(scopeService.canAccessStudent(adminId, studentOnEndpointInLabX)).isTrue();
            verify(endpointDeviceRepository, never()).existsByIdAndLab_IdIn(any(), any());
            verify(endpointDeviceRepository, never()).existsByAssignedStudent_IdAndLab_IdIn(any(), any());
        }

        @Test
        void bypassesExamModeAuthorizationCheck() {
            when(userRepository.findById(adminId)).thenReturn(Optional.of(userWithRole("ADMIN")));
            assertThat(scopeService.hasExamModeAuthorization(adminId, labX)).isTrue();
            verify(facultyAssignmentRepository, never())
                .existsByFacultyUser_IdAndLaboratory_IdAndExamModeAuthorizedTrue(any(), any());
        }
    }

    @Nested
    class FacultyAssignedScope {

        @Test
        void assignedLaboratoryIsAllowed() {
            stubFacultyAAssignedToLabXOnly();
            when(facultyAssignmentRepository.existsByFacultyUser_IdAndLaboratory_Id(facultyAId, labX)).thenReturn(true);

            assertThat(scopeService.canAccessLaboratory(facultyAId, labX)).isTrue();
        }

        @Test
        void endpointInAssignedLaboratoryIsAllowed() {
            stubFacultyAAssignedToLabXOnly();
            when(endpointDeviceRepository.existsByIdAndLab_IdIn(endpointInLabX, Set.of(labX))).thenReturn(true);

            assertThat(scopeService.canAccessEndpoint(facultyAId, endpointInLabX)).isTrue();
        }

        @Test
        void studentAssignedToEndpointInAssignedLaboratoryIsAllowed() {
            stubFacultyAAssignedToLabXOnly();
            when(endpointDeviceRepository.existsByAssignedStudent_IdAndLab_IdIn(studentOnEndpointInLabX, Set.of(labX)))
                .thenReturn(true);

            assertThat(scopeService.canAccessStudent(facultyAId, studentOnEndpointInLabX)).isTrue();
        }

        @Test
        void accessibleIdSetsAreDerivedNotStoredDirectly() {
            stubFacultyAAssignedToLabXOnly();
            when(endpointDeviceRepository.findIdsByLab_IdIn(Set.of(labX))).thenReturn(Set.of(endpointInLabX));
            when(endpointDeviceRepository.findAssignedStudentIdsByLab_IdIn(Set.of(labX)))
                .thenReturn(Set.of(studentOnEndpointInLabX));

            assertThat(scopeService.accessibleLaboratoryIds(facultyAId)).containsExactly(labX);
            assertThat(scopeService.accessibleEndpointIds(facultyAId)).containsExactly(endpointInLabX);
            assertThat(scopeService.accessibleStudentIds(facultyAId)).containsExactly(studentOnEndpointInLabX);
        }
    }

    @Nested
    class FacultyDeniedOutsideScope {

        @Test
        void unassignedLaboratoryIsDenied() {
            stubFacultyAAssignedToLabXOnly();
            when(facultyAssignmentRepository.existsByFacultyUser_IdAndLaboratory_Id(facultyAId, labY)).thenReturn(false);

            assertThat(scopeService.canAccessLaboratory(facultyAId, labY)).isFalse();
        }

        @Test
        void endpointInUnassignedLaboratoryIsDenied() {
            stubFacultyAAssignedToLabXOnly();
            when(endpointDeviceRepository.existsByIdAndLab_IdIn(endpointInLabY, Set.of(labX))).thenReturn(false);

            assertThat(scopeService.canAccessEndpoint(facultyAId, endpointInLabY)).isFalse();
        }

        @Test
        void studentBelongingToAnotherLaboratoryIsDenied() {
            stubFacultyAAssignedToLabXOnly();
            when(endpointDeviceRepository.existsByAssignedStudent_IdAndLab_IdIn(studentOnEndpointInLabY, Set.of(labX)))
                .thenReturn(false);

            assertThat(scopeService.canAccessStudent(facultyAId, studentOnEndpointInLabY)).isFalse();
        }

        /**
         * Explicit ID-manipulation / cross-faculty isolation check: Faculty
         * A must not obtain Faculty B's scope merely by supplying an ID
         * that belongs to B's lab/endpoint/student. Faculty A's resolved
         * scope is always {labX} regardless of what ID is passed in - the
         * repository stub only ever returns false for labY/endpointInLabY/
         * studentOnEndpointInLabY because A's allowed-ID set never
         * contains them, proving the check is against A's own resolved
         * scope and not against the supplied ID's apparent validity.
         */
        @Test
        void suppliedIdForAnotherFacultysLaboratoryDoesNotGrantAccess() {
            stubFacultyAAssignedToLabXOnly();
            when(facultyAssignmentRepository.existsByFacultyUser_IdAndLaboratory_Id(facultyAId, labY)).thenReturn(false);
            when(endpointDeviceRepository.existsByIdAndLab_IdIn(endpointInLabY, Set.of(labX))).thenReturn(false);
            when(endpointDeviceRepository.existsByAssignedStudent_IdAndLab_IdIn(studentOnEndpointInLabY, Set.of(labX)))
                .thenReturn(false);

            assertThat(scopeService.canAccessLaboratory(facultyAId, labY))
                .as("Faculty A supplying Faculty B's lab ID must be denied")
                .isFalse();
            assertThat(scopeService.canAccessEndpoint(facultyAId, endpointInLabY))
                .as("Faculty A supplying an endpoint ID from Faculty B's lab must be denied")
                .isFalse();
            assertThat(scopeService.canAccessStudent(facultyAId, studentOnEndpointInLabY))
                .as("Faculty A supplying a student ID from Faculty B's lab must be denied")
                .isFalse();
        }
    }

    @Nested
    class ExamMode {

        @Test
        void assignedLabWithExplicitAuthorizationIsAllowed() {
            when(userRepository.findById(facultyAId)).thenReturn(Optional.of(userWithRole("FACULTY")));
            when(facultyAssignmentRepository.existsByFacultyUser_IdAndLaboratory_IdAndExamModeAuthorizedTrue(facultyAId, labX))
                .thenReturn(true);

            assertThat(scopeService.hasExamModeAuthorization(facultyAId, labX)).isTrue();
        }

        @Test
        void assignedLabWithoutExplicitAuthorizationIsDenied() {
            when(userRepository.findById(facultyAId)).thenReturn(Optional.of(userWithRole("FACULTY")));
            when(facultyAssignmentRepository.existsByFacultyUser_IdAndLaboratory_IdAndExamModeAuthorizedTrue(facultyAId, labX))
                .thenReturn(false);

            assertThat(scopeService.hasExamModeAuthorization(facultyAId, labX)).isFalse();
        }

        @Test
        void examModeAuthorizedElsewhereDoesNotCarryOverToUnassignedLab() {
            // Faculty A has exam_mode_authorized=true for labX, but labY
            // is a *different* laboratory they hold no assignment for at
            // all. The query is keyed on (faculty, laboratory) together,
            // so a true flag on one row can never leak to another lab.
            when(userRepository.findById(facultyAId)).thenReturn(Optional.of(userWithRole("FACULTY")));
            when(facultyAssignmentRepository.existsByFacultyUser_IdAndLaboratory_IdAndExamModeAuthorizedTrue(facultyAId, labY))
                .thenReturn(false);

            assertThat(scopeService.hasExamModeAuthorization(facultyAId, labY)).isFalse();
        }
    }

    @Nested
    class NoRecognizedScope {

        @Test
        void unknownUserResolvesToEmptyScopeNotAnException() {
            UUID ghostId = UUID.randomUUID();
            when(userRepository.findById(ghostId)).thenReturn(Optional.empty());

            assertThat(scopeService.isGlobalScope(ghostId)).isFalse();
            assertThat(scopeService.accessibleLaboratoryIds(ghostId)).isEmpty();
        }
    }
}
