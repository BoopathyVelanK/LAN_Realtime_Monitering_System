package com.securesoc.controller;

import com.securesoc.dto.EndpointSummaryResponse;
import com.securesoc.service.EndpointService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Backs the frontend's dashboardApi.getEndpoints() (frontend/src/api/...)
 * once VITE_USE_MOCKS=false. Phase 4/5 (alerts, risk scoring, WebSocket
 * push) are not implemented yet - this only covers the "one endpoint
 * checking in" milestone from the Phase 2 build plan. */
@RestController
@RequestMapping("/endpoints")
public class EndpointController {

    private final EndpointService endpointService;

    public EndpointController(EndpointService endpointService) {
        this.endpointService = endpointService;
    }

    @GetMapping
    public List<EndpointSummaryResponse> listEndpoints() {
        return endpointService.listAll();
    }
}
