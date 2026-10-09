package com.securesoc.security;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * F7 - the SUBSCRIBE gate in isolation. A real unauthenticated STOMP client
 * can never reach this (the handshake already rejects it - see
 * RbacWebSocketIntegrationTest), so the dropped-frame branch is only
 * reachable by driving the interceptor directly.
 */
class WebSocketSubscriptionInterceptorTest {

    private final WebSocketSubscriptionInterceptor interceptor = new WebSocketSubscriptionInterceptor();
    private final MessageChannel channel = mock(MessageChannel.class);

    private Message<byte[]> subscribeFrame(boolean authenticated) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-1");
        accessor.setSubscriptionId("sub-0");
        accessor.setDestination("/user/queue/alerts");
        if (authenticated) {
            accessor.setUser(() -> "rbac_faculty_f");
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> frame(StompCommand command, String destination, boolean authenticated) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("session-1");
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (authenticated) {
            accessor.setUser(() -> "rbac_faculty_f");
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void authenticatedSend_isDropped_toEveryDestinationClass() {
        for (String destination : new String[] {"/topic/test", "/queue/alerts", "/user/rbac_admin/queue/alerts", "/app/anything"}) {
            assertNull(interceptor.preSend(frame(StompCommand.SEND, destination, true), channel), destination);
        }
    }

    @Test
    void unauthenticatedSend_isDropped() {
        assertNull(interceptor.preSend(frame(StompCommand.SEND, "/topic/test", false), channel));
    }

    @Test
    void clientForgedMessageFrame_isDropped() {
        assertNull(interceptor.preSend(frame(StompCommand.MESSAGE, "/user/rbac_admin/queue/alerts", true), channel));
    }

    @Test
    void connectUnsubscribeDisconnect_stillPass() {
        assertNotNull(interceptor.preSend(frame(StompCommand.CONNECT, null, true), channel));
        assertNotNull(interceptor.preSend(frame(StompCommand.UNSUBSCRIBE, null, true), channel));
        assertNotNull(interceptor.preSend(frame(StompCommand.DISCONNECT, null, true), channel));
    }

    @Test
    void subscribeWithoutPrincipal_isDropped() {
        assertNull(interceptor.preSend(subscribeFrame(false), channel));
    }

    @Test
    void subscribeWithPrincipal_passesThrough() {
        assertNotNull(interceptor.preSend(subscribeFrame(true), channel));
    }
}
