package com.securesoc.config;

import com.securesoc.security.AgentTokenAuthFilter;
import com.securesoc.security.JwtAuthenticationFilter;
import com.securesoc.security.RestAuthenticationEntryPoint;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.authentication.ProviderManager;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AgentTokenAuthFilter agentTokenAuthFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final CorsProperties corsProperties;

    public SecurityConfig(
        JwtAuthenticationFilter jwtAuthenticationFilter,
        AgentTokenAuthFilter agentTokenAuthFilter,
        RestAuthenticationEntryPoint restAuthenticationEntryPoint,
        CorsProperties corsProperties
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.agentTokenAuthFilter = agentTokenAuthFilter;
        this.corsProperties = corsProperties;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // stateless JWT API, no cookies/session to forge
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(restAuthenticationEntryPoint)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login", "/auth/refresh", "/agents/register").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // SockJS's own negotiation requests (GET /ws/info,
                        // then the actual XHR-streaming/WebSocket transport
                        // requests under /ws/**) carry no Authorization
                        // header - auth for this path is handled entirely
                        // by WebSocketAuthHandshakeInterceptor's ?token=
                        // query-param check at the handshake layer (see
                        // WebSocketConfig), not by this filter chain. This
                        // permitAll only lets the HTTP upgrade/negotiation
                        // requests through; it does not weaken WebSocket
                        // auth itself, since a rejected handshake still
                        // returns 401 from the interceptor before any STOMP
                        // session or topic subscription exists. All other
                        // REST endpoints are unaffected by this change.
                        .requestMatchers("/ws/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(agentTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(corsProperties.allowedOrigins().split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Agent-Token", "X-Registration-Secret"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
