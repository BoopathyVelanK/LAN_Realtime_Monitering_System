package com.securesoc.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Turns the username WebSocketAuthHandshakeInterceptor already validated
 * and stashed in the handshake attributes into a real STOMP session
 * Principal, instead of leaving every WebSocket session anonymous.
 * Nothing in Phase 6 currently branches on this Principal (the one topic,
 * /topic/endpoints/status, is a broadcast to every connected client) -
 * this exists so Phase 7 (RBAC) can add per-role/per-user destinations
 * later without having to first retrofit session identity.
 */
@Component
public class PrincipalHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    @Nullable
    protected Principal determineUser(
        @NonNull ServerHttpRequest request,
        @NonNull WebSocketHandler wsHandler,
        @NonNull Map<String, Object> attributes
    ) {
        Object username = attributes.get(WebSocketAuthHandshakeInterceptor.USERNAME_ATTRIBUTE);
        if (!(username instanceof String usernameStr) || usernameStr.isBlank()) {
            return super.determineUser(request, wsHandler, attributes);
        }
        return new StompPrincipal(usernameStr);
    }

    private record StompPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
