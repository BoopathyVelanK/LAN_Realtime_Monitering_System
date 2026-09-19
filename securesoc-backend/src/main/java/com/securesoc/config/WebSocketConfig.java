package com.securesoc.config;

import com.securesoc.security.PrincipalHandshakeHandler;
import com.securesoc.security.WebSocketAuthHandshakeInterceptor;
import com.securesoc.security.WebSocketSubscriptionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Real-time layer. RBAC Phase 3: alerts/risk/endpoint-status are
 * delivered per-user via /queue/... destinations (see
 * ScopedWebSocketDelivery), not broadcast on a shared /topic - a Faculty
 * caller only ever receives events for endpoints within their assigned
 * laboratory scope. "/topic" is kept enabled on the broker for any future
 * genuinely-global broadcast need, but nothing in this codebase publishes
 * to it today.
 *
 * The REST API's JWT auth (SecurityConfig, JwtAuthenticationFilter) is
 * completely unmodified by this class - WebSocket auth is a parallel,
 * narrower check at handshake time (see WebSocketAuthHandshakeInterceptor)
 * plus a coarse SUBSCRIBE-level check (see WebSocketSubscriptionInterceptor).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthHandshakeInterceptor authHandshakeInterceptor;
    private final PrincipalHandshakeHandler principalHandshakeHandler;
    private final WebSocketSubscriptionInterceptor subscriptionInterceptor;
    private final CorsProperties corsProperties;

    public WebSocketConfig(
        WebSocketAuthHandshakeInterceptor authHandshakeInterceptor,
        PrincipalHandshakeHandler principalHandshakeHandler,
        WebSocketSubscriptionInterceptor subscriptionInterceptor,
        CorsProperties corsProperties
    ) {
        this.authHandshakeInterceptor = authHandshakeInterceptor;
        this.principalHandshakeHandler = principalHandshakeHandler;
        this.subscriptionInterceptor = subscriptionInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(subscriptionInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Registered as "/ws" - server.servlet.context-path=/api applies
        // automatically (Spring registers this through the same
        // DispatcherServlet), so the real path is /api/ws, matching
        // frontend/src/config.ts's WS_URL derivation exactly.
        registry.addEndpoint("/ws")
            .setAllowedOrigins(corsProperties.allowedOrigins().split(","))
            .addInterceptors(authHandshakeInterceptor)
            .setHandshakeHandler(principalHandshakeHandler)
            .withSockJS();
    }
}
