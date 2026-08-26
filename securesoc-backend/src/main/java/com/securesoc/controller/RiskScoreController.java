package com.securesoc.controller;

import com.securesoc.dto.RiskScoreResponse;
import com.securesoc.service.RiskScoreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Backs the frontend's dashboardApi.getAllRiskScores()/getRiskScore()
 * (frontend/src/api/dashboardApi.ts) once those are switched off their
 * current MOCK-ONLY behavior. Checkpoint C - read-only exposure of scores
 * already persisted by RiskScoreService.recordDetection (Checkpoint B);
 * this controller never writes a RiskScore and never synthesizes a
 * default SAFE/0 response for an endpoint that has no row yet - see
 * RiskScoreService's javadoc. A missing row is a 404
 * (ResourceNotFoundException, handled by GlobalExceptionHandler), same as
 * every other not-found case on this API. */
@RestController
@RequestMapping("/risk-scores")
public class RiskScoreController {

    private final RiskScoreService riskScoreService;

    public RiskScoreController(RiskScoreService riskScoreService) {
        this.riskScoreService = riskScoreService;
    }

    @GetMapping
    public List<RiskScoreResponse> listRiskScores() {
        return riskScoreService.getAll();
    }

    @GetMapping("/{endpointId}")
    public RiskScoreResponse getRiskScore(@PathVariable UUID endpointId) {
        return riskScoreService.getForEndpoint(endpointId);
    }
}
