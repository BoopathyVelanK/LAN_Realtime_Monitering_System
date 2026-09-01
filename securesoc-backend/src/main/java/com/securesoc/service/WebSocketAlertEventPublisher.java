package com.securesoc.service;

import com.securesoc.dto.AlertResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes {@link AlertResponse} payloads to {@code /topic/alerts} after
 * the enclosing database transaction has successfully committed.
 *
 * <p>Publishing is deferred to {@link TransactionSynchronization#afterCommit()}
 * so that a transaction that later rolls back never causes the frontend to
 * display an alert that was never actually persisted. When no transaction is
 * active (e.g. during unit tests that call the publisher directly), the
 * message is sent synchronously as a safe fallback.
 *
 * <p>Follows the same single-responsibility pattern as
 * {@link WebSocketEndpointEventPublisher}: one class owns the topic name and
 * the {@link SimpMessagingTemplate} call; callers never touch the template
 * directly. The {@code AlertResponse} DTO is already built by the caller
 * (AlertService) using the existing {@code toResponse()} mapping, so this
 * class adds no new mapping logic.
 */
@Service
public class WebSocketAlertEventPublisher {

    static final String ALERT_TOPIC = "/topic/alerts";

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketAlertEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Sends {@code alert} to {@value #ALERT_TOPIC} after the current
     * transaction commits. If no transaction is active the message is sent
     * immediately.
     */
    public void publishAlert(AlertResponse alert) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend(ALERT_TOPIC, alert);
                }
            });
        } else {
            messagingTemplate.convertAndSend(ALERT_TOPIC, alert);
        }
    }
}
