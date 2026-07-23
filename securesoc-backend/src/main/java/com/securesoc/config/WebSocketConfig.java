package com.securesoc.config;

import com.securesoc.security.PrincipalHandshakeHandler;
import com.securesoc.security.WebSocketAuthHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Phase 6 - real-time layer. A single broadcast topic for now
 * (/topic/endpoints/status - see EndpointStatusEvent's Javadoc for why
 * that exact path, matching the frontend contract that already existed
 * in frontend/src/ws/stompClient.ts before this backend support did).
 * Alerts/risk topics are deliberately NOT added here - no backend source
 * for that data exists yet (see AlertResponse/RiskScoreResponse in the
 * frontend's types/api.ts, marked CONTRACT-FIRST); adding those topics
 * now would be dead infrastructure until that engine exists.
 *
 * The REST API's JWT auth (SecurityConfig, JwtAuthenticationFilter) is
 * completely unmodified by this class - WebSocket auth is a parallel,
 * narrower check at handshake time only (see
 * WebSocketAuthHandshakeInterceptor).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthHandshakeInterceptor authHandshakeInterceptor;
    private final PrincipalHandshakeHandler principalHandshakeHandler;
    private final CorsProperties corsProperties;

    public WebSocketConfig(
        WebSocketAuthHandshakeInterceptor authHandshakeInterceptor,
        PrincipalHandshakeHandler principalHandshakeHandler,
        CorsProperties corsProperties
    ) {
        this.authHandshakeInterceptor = authHandshakeInterceptor;
        this.principalHandshakeHandler = principalHandshakeHandler;
        this.corsProperties = corsProperties;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
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
