package com.securesoc.controller;

import com.securesoc.dto.AlertResponse;
import com.securesoc.security.SecurityUserDetails;
import com.securesoc.service.AlertService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public List<AlertResponse> getAlerts(
            @RequestParam(required = false) UUID endpointId,
            @RequestParam(required = false) String status) {
        return alertService.getAlerts(endpointId, status);
    }

    @GetMapping("/{id}")
    public AlertResponse getAlertById(@PathVariable UUID id) {
        return alertService.getAlertById(id);
    }

    @PostMapping("/{id}/acknowledge")
    public AlertResponse acknowledgeAlert(
            @PathVariable UUID id,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {
        return alertService.acknowledgeAlert(id, userDetails.getId());
    }

    @PostMapping("/{id}/resolve")
    public AlertResponse resolveAlert(@PathVariable UUID id) {
        return alertService.resolveAlert(id);
    }
}
