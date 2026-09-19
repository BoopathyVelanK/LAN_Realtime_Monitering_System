package com.securesoc.controller;

import com.securesoc.dto.AlertResponse;
import com.securesoc.security.SecurityUserDetails;
import com.securesoc.service.AlertService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Faculty scope (which alerts a given Faculty caller may see/mutate) is
 * enforced in AlertService via FacultyScopeService - this class only
 * gates the coarse role check and resolves the authenticated caller. */
@RestController
@RequestMapping("/alerts")
@PreAuthorize("hasAnyRole('ADMIN','FACULTY')")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public List<AlertResponse> getAlerts(
            @RequestParam(required = false) UUID endpointId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {
        return alertService.getAlerts(endpointId, status, userDetails.getId());
    }

    @GetMapping("/{id}")
    public AlertResponse getAlertById(
            @PathVariable UUID id,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {
        return alertService.getAlertById(id, userDetails.getId());
    }

    @PostMapping("/{id}/acknowledge")
    public AlertResponse acknowledgeAlert(
            @PathVariable UUID id,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {
        return alertService.acknowledgeAlert(id, userDetails.getId());
    }

    @PostMapping("/{id}/resolve")
    public AlertResponse resolveAlert(
            @PathVariable UUID id,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {
        return alertService.resolveAlert(id, userDetails.getId());
    }
}
