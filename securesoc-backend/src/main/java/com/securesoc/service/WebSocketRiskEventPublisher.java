package com.securesoc.service;

import com.securesoc.dto.RiskScoreResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes {@link RiskScoreResponse} payloads, scoped per-recipient,
 * after the enclosing database transaction has successfully committed -
 * same rationale and delegation to {@link ScopedWebSocketDelivery} as
 * {@link WebSocketAlertEventPublisher}. When no transaction is active the
 * message is sent synchronously as a safe fallback.
 *
 * <p>The {@link RiskScoreResponse} DTO is already built by the caller
 * (RiskScoreService) using the existing {@code toResponse()} mapping;
 * this class adds no new mapping logic.
 */
@Service
public class WebSocketRiskEventPublisher {

    static final String RISK_DESTINATION = "/queue/risk";

    private final ScopedWebSocketDelivery scopedDelivery;

    public WebSocketRiskEventPublisher(ScopedWebSocketDelivery scopedDelivery) {
        this.scopedDelivery = scopedDelivery;
    }

    /**
     * Delivers {@code risk} to every currently connected user authorized
     * to see it (see {@link ScopedWebSocketDelivery}) after the current
     * transaction commits. If no transaction is active the message is
     * sent immediately.
     */
    public void publishRiskScore(RiskScoreResponse risk) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    scopedDelivery.deliverToAuthorizedUsers(RISK_DESTINATION, risk, risk.endpointId());
                }
            });
        } else {
            scopedDelivery.deliverToAuthorizedUsers(RISK_DESTINATION, risk, risk.endpointId());
        }
    }
}
