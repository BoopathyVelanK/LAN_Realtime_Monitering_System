package com.securesoc.service;

import com.securesoc.dto.RiskScoreResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes {@link RiskScoreResponse} payloads to {@code /topic/risk} after
 * the enclosing database transaction has successfully committed.
 *
 * <p>Publishing is deferred to {@link TransactionSynchronization#afterCommit()}
 * for the same reason as {@link WebSocketAlertEventPublisher}: a transaction
 * that rolls back must never produce a frontend risk-score update that was
 * never actually persisted. When no transaction is active the message is sent
 * synchronously as a safe fallback.
 *
 * <p>Follows the same pattern as {@link WebSocketEndpointEventPublisher} and
 * {@link WebSocketAlertEventPublisher}. The {@link RiskScoreResponse} DTO is
 * already built by the caller (RiskScoreService) using the existing
 * {@code toResponse()} mapping; this class adds no new mapping logic.
 */
@Service
public class WebSocketRiskEventPublisher {

    static final String RISK_TOPIC = "/topic/risk";

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketRiskEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Sends {@code risk} to {@value #RISK_TOPIC} after the current transaction
     * commits. If no transaction is active the message is sent immediately.
     */
    public void publishRiskScore(RiskScoreResponse risk) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend(RISK_TOPIC, risk);
                }
            });
        } else {
            messagingTemplate.convertAndSend(RISK_TOPIC, risk);
        }
    }
}
