package com.securesoc.controller;

import com.securesoc.dto.EndpointSummaryResponse;
import com.securesoc.security.SecurityUserDetails;
import com.securesoc.service.EndpointService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Backs the frontend's dashboardApi.getEndpoints() (frontend/src/api/...)
 * once VITE_USE_MOCKS=false. Phase 4/5 (alerts, risk scoring, WebSocket
 * push) are not implemented yet - this only covers the "one endpoint
 * checking in" milestone from the Phase 2 build plan.
 *
 * Faculty scope (which endpoints a given Faculty caller may see) is
 * enforced in EndpointService via FacultyScopeService - this class only
 * gates the coarse role check and resolves the authenticated caller. */
@RestController
@RequestMapping("/endpoints")
@PreAuthorize("hasAnyRole('ADMIN','FACULTY')")
public class EndpointController {

    private final EndpointService endpointService;

    public EndpointController(EndpointService endpointService) {
        this.endpointService = endpointService;
    }

    @GetMapping
    public List<EndpointSummaryResponse> listEndpoints(@AuthenticationPrincipal SecurityUserDetails userDetails) {
        return endpointService.listAll(userDetails.getId());
    }
}
