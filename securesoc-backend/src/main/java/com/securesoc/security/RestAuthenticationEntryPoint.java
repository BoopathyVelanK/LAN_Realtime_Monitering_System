package com.securesoc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securesoc.dto.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        // TEMP DEBUG (Phase 6 /ws/info 401 investigation - remove once resolved)
        log.info("[WS-DEBUG][RestAuthenticationEntryPoint] commence() called for method={} requestURI={} exceptionType={} exceptionMessage={}",
            request.getMethod(), request.getRequestURI(),
            authException.getClass().getSimpleName(), authException.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiErrorResponse body = new ApiErrorResponse(
        Instant.now(),
        HttpServletResponse.SC_UNAUTHORIZED,
        "Unauthorized",
        "Authentication is required or the access token is invalid/expired.",
        request.getRequestURI()
);

objectMapper.writeValue(response.getOutputStream(), body);
    }
}
