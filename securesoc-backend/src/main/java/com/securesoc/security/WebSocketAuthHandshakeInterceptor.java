package com.securesoc.security;

import com.securesoc.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Validates the JWT access token before a SockJS/STOMP handshake is
 * allowed to complete. Deliberately reuses JwtService.parseAndValidate -
 * the exact same validation JwtAuthenticationFilter runs for REST calls -
 * so WebSocket auth can never silently drift from REST auth.
 *
 * Token transport: a SockJS query parameter (?token=...), NOT a STOMP
 * CONNECT header. This matches the existing frontend client
 * (frontend/src/ws/stompClient.ts), which was already written this way
 * before this backend support existed - adapting to the client here
 * rather than changing already-working frontend code. SockJS's HTTP
 * fallback transports (xhr-streaming etc.) can't reliably carry a custom
 * Authorization header on every transport, which is the usual reason
 * this query-param pattern is used for SockJS specifically (a native
 * WebSocket-only client could use a CONNECT header instead).
 *
 * On any failure (missing/malformed/expired token, disabled/locked user)
 * the handshake is rejected outright with 401 - no anonymous WebSocket
 * connections are ever established, unlike the REST filter chain's
 * fall-through-to-anonymous behavior for public endpoints (there are no
 * public WebSocket topics).
 */
@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    static final String USER_ID_ATTRIBUTE = "userId";
    static final String USERNAME_ATTRIBUTE = "username";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public WebSocketAuthHandshakeInterceptor(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public boolean beforeHandshake(
        @NonNull ServerHttpRequest request,
        @NonNull ServerHttpResponse response,
        @NonNull WebSocketHandler wsHandler,
        @NonNull Map<String, Object> attributes
    ) {
        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Claims claims = jwtService.parseAndValidate(token);
            UUID userId = UUID.fromString(claims.getSubject());

            boolean userValid = userRepository.findById(userId)
                .map(user -> {
                    SecurityUserDetails principal = new SecurityUserDetails(user);
                    return principal.isEnabled() && principal.isAccountNonLocked();
                })
                .orElse(false);

            if (!userValid) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            // Consumed by PrincipalHandshakeHandler right after this method
            // returns, to give the STOMP session a real Principal rather
            // than an anonymous one - groundwork for Phase 7's role-scoped
            // subscriptions, unused for anything beyond that today.
            attributes.put(USER_ID_ATTRIBUTE, userId.toString());
            attributes.put(USERNAME_ATTRIBUTE, claims.get("username", String.class));
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
        @NonNull ServerHttpRequest request,
        @NonNull ServerHttpResponse response,
        @NonNull WebSocketHandler wsHandler,
        @Nullable Exception exception
    ) {
        // No-op - nothing to clean up; attributes written above live on
        // for the lifetime of the WebSocket session by design.
    }

    private String extractToken(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            if (eq > 0 && "token".equals(param.substring(0, eq))) {
                return URLDecoder.decode(param.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
