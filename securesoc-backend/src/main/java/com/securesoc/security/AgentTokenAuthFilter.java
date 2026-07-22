package com.securesoc.security;

import com.securesoc.repository.EndpointDeviceRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Endpoint agents (agent.py) authenticate with a per-device opaque token in
 * the X-Agent-Token header - never a JWT, never the user login flow. Only
 * applies to /agents/heartbeat and /monitoring/** (see SecurityConfig);
 * /agents/register is intentionally open, authenticated instead by the
 * shared X-Registration-Secret header checked in AgentService.
 */
@Component
public class AgentTokenAuthFilter extends OncePerRequestFilter {

    public static final String AGENT_TOKEN_HEADER = "X-Agent-Token";

    private final EndpointDeviceRepository endpointDeviceRepository;

    public AgentTokenAuthFilter(EndpointDeviceRepository endpointDeviceRepository) {
        this.endpointDeviceRepository = endpointDeviceRepository;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String token = request.getHeader(AGENT_TOKEN_HEADER);

        if (token != null && !token.isBlank()) {
            String tokenHash = TokenHasher.sha256Hex(token);
            endpointDeviceRepository.findByAgentTokenHash(tokenHash).ifPresent(device -> {
                var authentication = new UsernamePasswordAuthenticationToken(
                    device, null, List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute("endpointDevice", device);
            });
        }

        filterChain.doFilter(request, response);
    }

    /** Only run on agent-facing paths - the JWT filter handles everything
     * else, so a malformed/absent X-Agent-Token header never affects a
     * normal user-facing request. */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return !(path.startsWith("/agents/heartbeat") || path.startsWith("/monitoring/"));
    }
}
