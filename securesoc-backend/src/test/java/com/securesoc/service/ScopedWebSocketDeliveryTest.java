package com.securesoc.service;

import com.securesoc.entity.User;
import com.securesoc.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Only FacultyScopeService is mocked here (as a collaborator, matching
 * this codebase's existing testing style, e.g. DepartmentServiceTest) -
 * the actual eligibility branching in ScopedWebSocketDelivery.isEligible()
 * runs for real for every case below, including the cross-lab isolation
 * case, which is the most important one.
 */
@ExtendWith(MockitoExtension.class)
class ScopedWebSocketDeliveryTest {

    private static final String DESTINATION = "/queue/alerts";

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private SimpUserRegistry userRegistry;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FacultyScopeService facultyScopeService;

    private ScopedWebSocketDelivery delivery;

    private UUID adminId;
    private UUID facultyAId;
    private UUID facultyBId;
    private UUID endpointInLabA;
    private UUID endpointInLabB;

    @BeforeEach
    void setUp() {
        delivery = new ScopedWebSocketDelivery(messagingTemplate, userRegistry, userRepository, facultyScopeService);
        adminId = UUID.randomUUID();
        facultyAId = UUID.randomUUID();
        facultyBId = UUID.randomUUID();
        endpointInLabA = UUID.randomUUID();
        endpointInLabB = UUID.randomUUID();
    }

    private SimpUser connected(String username) {
        SimpUser user = mock(SimpUser.class);
        when(user.getName()).thenReturn(username);
        return user;
    }

    private User userEntity(UUID id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    @Test
    void adminReceivesEventForAnyEndpoint() {
        when(userRegistry.getUsers()).thenReturn(Set.of(connected("admin1")));
        when(userRepository.findByUsername("admin1")).thenReturn(Optional.of(userEntity(adminId)));
        when(facultyScopeService.isGlobalScope(adminId)).thenReturn(true);

        Object payload = new Object();
        delivery.deliverToAuthorizedUsers(DESTINATION, payload, endpointInLabA);

        verify(messagingTemplate).convertAndSendToUser("admin1", DESTINATION, payload);
    }

    @Test
    void adminReceivesEventWithNullEndpointId() {
        when(userRegistry.getUsers()).thenReturn(Set.of(connected("admin1")));
        when(userRepository.findByUsername("admin1")).thenReturn(Optional.of(userEntity(adminId)));
        when(facultyScopeService.isGlobalScope(adminId)).thenReturn(true);

        Object payload = new Object();
        delivery.deliverToAuthorizedUsers(DESTINATION, payload, null);

        verify(messagingTemplate).convertAndSendToUser("admin1", DESTINATION, payload);
    }

    @Test
    void facultyAssignedToLabA_receivesEventFromEndpointInLabA() {
        when(userRegistry.getUsers()).thenReturn(Set.of(connected("facultyA")));
        when(userRepository.findByUsername("facultyA")).thenReturn(Optional.of(userEntity(facultyAId)));
        when(facultyScopeService.isGlobalScope(facultyAId)).thenReturn(false);
        when(facultyScopeService.canAccessEndpoint(facultyAId, endpointInLabA)).thenReturn(true);

        Object payload = new Object();
        delivery.deliverToAuthorizedUsers(DESTINATION, payload, endpointInLabA);

        verify(messagingTemplate).convertAndSendToUser("facultyA", DESTINATION, payload);
    }

    /**
     * The most important test: explicit cross-lab isolation. Faculty A is
     * assigned to Lab A only; an event from an endpoint in Lab B must
     * never be delivered to them.
     */
    @Test
    void facultyAssignedToLabA_doesNotReceiveEventFromEndpointInLabB() {
        when(userRegistry.getUsers()).thenReturn(Set.of(connected("facultyA")));
        when(userRepository.findByUsername("facultyA")).thenReturn(Optional.of(userEntity(facultyAId)));
        when(facultyScopeService.isGlobalScope(facultyAId)).thenReturn(false);
        when(facultyScopeService.canAccessEndpoint(facultyAId, endpointInLabB)).thenReturn(false);

        delivery.deliverToAuthorizedUsers(DESTINATION, new Object(), endpointInLabB);

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void facultyReceivesNothingWhenEndpointIdIsNull() {
        when(userRegistry.getUsers()).thenReturn(Set.of(connected("facultyA")));
        when(userRepository.findByUsername("facultyA")).thenReturn(Optional.of(userEntity(facultyAId)));
        when(facultyScopeService.isGlobalScope(facultyAId)).thenReturn(false);

        delivery.deliverToAuthorizedUsers(DESTINATION, new Object(), null);

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
        verify(facultyScopeService, never()).canAccessEndpoint(any(), any());
    }

    @Test
    void facultyWithZeroAssignments_receivesNoScopedEvents() {
        when(userRegistry.getUsers()).thenReturn(Set.of(connected("facultyA")));
        when(userRepository.findByUsername("facultyA")).thenReturn(Optional.of(userEntity(facultyAId)));
        when(facultyScopeService.isGlobalScope(facultyAId)).thenReturn(false);
        when(facultyScopeService.canAccessEndpoint(facultyAId, endpointInLabA)).thenReturn(false);

        delivery.deliverToAuthorizedUsers(DESTINATION, new Object(), endpointInLabA);

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void unknownConnectedPrincipal_failsClosedAndIsSkipped() {
        when(userRegistry.getUsers()).thenReturn(Set.of(connected("ghost")));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        delivery.deliverToAuthorizedUsers(DESTINATION, new Object(), endpointInLabA);

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
        verifyNoInteractions(facultyScopeService);
    }

    @Test
    void multipleConnectedUsers_onlyEligibleOnesReceiveDelivery() {
        when(userRegistry.getUsers()).thenReturn(Set.of(connected("admin1"), connected("facultyA"), connected("facultyB")));
        when(userRepository.findByUsername("admin1")).thenReturn(Optional.of(userEntity(adminId)));
        when(userRepository.findByUsername("facultyA")).thenReturn(Optional.of(userEntity(facultyAId)));
        when(userRepository.findByUsername("facultyB")).thenReturn(Optional.of(userEntity(facultyBId)));
        when(facultyScopeService.isGlobalScope(adminId)).thenReturn(true);
        when(facultyScopeService.isGlobalScope(facultyAId)).thenReturn(false);
        when(facultyScopeService.isGlobalScope(facultyBId)).thenReturn(false);
        when(facultyScopeService.canAccessEndpoint(facultyAId, endpointInLabA)).thenReturn(true);
        when(facultyScopeService.canAccessEndpoint(facultyBId, endpointInLabA)).thenReturn(false);

        Object payload = new Object();
        delivery.deliverToAuthorizedUsers(DESTINATION, payload, endpointInLabA);

        verify(messagingTemplate).convertAndSendToUser("admin1", DESTINATION, payload);
        verify(messagingTemplate).convertAndSendToUser("facultyA", DESTINATION, payload);
        verify(messagingTemplate, never()).convertAndSendToUser(eq("facultyB"), any(), any());
    }
}
