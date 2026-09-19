package com.securesoc.service;

import com.securesoc.entity.User;
import com.securesoc.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The single place that decides, per WebSocket event, which currently
 * connected users are authorized to receive it, and delivers it to
 * exactly those users via per-user queues - never a shared broadcast
 * topic. Used by WebSocketAlertEventPublisher, WebSocketRiskEventPublisher,
 * and WebSocketEndpointEventPublisher so the eligibility rule below is
 * defined and tested in exactly one place rather than duplicated three
 * times.
 *
 * Eligibility rule, evaluated fresh for every event (no caching - see
 * FacultyScopeService's own "always resolve scope through this service"
 * philosophy, which this follows):
 *   - ADMIN (FacultyScopeService.isGlobalScope): always eligible.
 *   - FACULTY: eligible only if endpointId is non-null AND
 *     FacultyScopeService.canAccessEndpoint(userId, endpointId) is true.
 *   - A null endpointId is therefore delivered to Admin only - never
 *     guessed at for Faculty.
 *   - A connected session whose Principal name no longer resolves to a
 *     User row (e.g. deleted between connect and this event) is skipped
 *     entirely - fail closed, not delivered to.
 *
 * "Connected user" comes from Spring's own SimpUserRegistry (populated
 * automatically by the STOMP broker for every active session) rather
 * than any bookkeeping this class does itself. This does mean one
 * UserRepository lookup per currently-connected session per event; that
 * cost is bounded by how many users are connected right now (typically a
 * handful in this deployment), not by the size of the users table, and
 * keeping it uncached matches FacultyScopeService's existing
 * always-resolve-fresh philosophy rather than adding a second,
 * potentially-stale source of truth.
 */
@Component
class ScopedWebSocketDelivery {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;
    private final UserRepository userRepository;
    private final FacultyScopeService facultyScopeService;

    ScopedWebSocketDelivery(
        SimpMessagingTemplate messagingTemplate,
        SimpUserRegistry userRegistry,
        UserRepository userRepository,
        FacultyScopeService facultyScopeService
    ) {
        this.messagingTemplate = messagingTemplate;
        this.userRegistry = userRegistry;
        this.userRepository = userRepository;
        this.facultyScopeService = facultyScopeService;
    }

    /**
     * Delivers {@code payload} to {@code destination} (a /queue/... path,
     * WITHOUT the "/user" prefix - see
     * SimpMessagingTemplate#convertAndSendToUser) for every currently
     * connected user eligible per the rule above, given the event's
     * {@code endpointId} (nullable).
     */
    void deliverToAuthorizedUsers(String destination, Object payload, UUID endpointId) {
        for (SimpUser connectedUser : userRegistry.getUsers()) {
            String username = connectedUser.getName();
            userRepository.findByUsername(username).ifPresent(user -> {
                if (isEligible(user, endpointId)) {
                    messagingTemplate.convertAndSendToUser(username, destination, payload);
                }
            });
        }
    }

    private boolean isEligible(User user, UUID endpointId) {
        UUID userId = user.getId();
        if (facultyScopeService.isGlobalScope(userId)) {
            return true;
        }
        return endpointId != null && facultyScopeService.canAccessEndpoint(userId, endpointId);
    }
}
