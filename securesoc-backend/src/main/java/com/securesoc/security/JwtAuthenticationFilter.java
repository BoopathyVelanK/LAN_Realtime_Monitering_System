package com.securesoc.security;

import com.securesoc.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parseAndValidate(token);
                UUID userId = UUID.fromString(claims.getSubject());

                // Re-fetching from the DB (rather than trusting the JWT's
                // embedded roles claim for authorization) means a role
                // change or account disable takes effect on the very next
                // request, not only after the access token expires.
                userRepository.findById(userId).ifPresent(user -> {
                    SecurityUserDetails principal = new SecurityUserDetails(user);
                    if (principal.isEnabled() && principal.isAccountNonLocked()) {
                        var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                });
            } catch (JwtException | IllegalArgumentException ex) {
                // Invalid/expired token: leave the SecurityContext empty so
                // the request falls through as anonymous/unauthenticated
                // rather than throwing here - Spring Security's entry point
                // turns that into a clean 401.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
