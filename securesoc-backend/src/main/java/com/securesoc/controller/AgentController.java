package com.securesoc.controller;

import com.securesoc.dto.agent.AgentHeartbeatRequest;
import com.securesoc.dto.agent.AgentHeartbeatResponse;
import com.securesoc.dto.agent.AgentRegisterRequest;
import com.securesoc.dto.agent.AgentRegisterResponse;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.service.AgentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Endpoints consumed exclusively by securesoc-agent/agent.py - see that
 * project's collector.py for the exact request payload shapes and
 * agent.py's register_with_retry/send_heartbeat for how responses are used. */
@RestController
@RequestMapping("/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/register")
    public ResponseEntity<AgentRegisterResponse> register(
        @Valid @RequestBody AgentRegisterRequest request,
        @RequestHeader("X-Registration-Secret") String registrationSecret
    ) {
        return ResponseEntity.ok(agentService.register(request, registrationSecret));
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<AgentHeartbeatResponse> heartbeat(
        @Valid @RequestBody AgentHeartbeatRequest request,
        HttpServletRequest httpRequest
    ) {
        // AgentTokenAuthFilter has already resolved + authenticated the
        // device and stashed it as a request attribute before this method
        // runs; a missing/invalid X-Agent-Token never reaches here at all
        // (SecurityConfig requires authentication on this path).
        EndpointDevice device = (EndpointDevice) httpRequest.getAttribute("endpointDevice");
        return ResponseEntity.ok(agentService.heartbeat(device, request));
    }
}
