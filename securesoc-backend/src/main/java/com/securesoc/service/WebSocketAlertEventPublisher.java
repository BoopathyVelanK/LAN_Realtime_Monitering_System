package com.securesoc.service;

import com.securesoc.dto.AlertResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes {@link AlertResponse} payloads, scoped per-recipient, after
 * the enclosing database transaction has successfully committed.
 *
 * <p>Publishing is deferred to {@link TransactionSynchronization#afterCommit()}
 * so that a transaction that later rolls back never causes the frontend to
 * display an alert that was never actually persisted. When no transaction is
 * active (e.g. during unit tests that call the publisher directly), the
 * message is sent synchronously as a safe fallback.
 *
 * <p>Delivery itself - who is authorized to receive this specific alert -
 * is delegated to {@link ScopedWebSocketDelivery}; this class only owns
 * the destination name and when to trigger delivery (see that class for
 * the Admin/Faculty eligibility rule, including the endpointId == null
 * fail-closed-for-Faculty case). The {@code AlertResponse} DTO is already
 * built by the caller (AlertService) using the existing {@code toResponse()}
 * mapping, so this class adds no new mapping logic.
 */
@Service
public class WebSocketAlertEventPublisher {

    static final String ALERT_DESTINATION = "/queue/alerts";

    private final ScopedWebSocketDelivery scopedDelivery;

    public WebSocketAlertEventPublisher(ScopedWebSocketDelivery scopedDelivery) {
        this.scopedDelivery = scopedDelivery;
    }

    /**
     * Delivers {@code alert} to every currently connected user authorized
     * to see it (see {@link ScopedWebSocketDelivery}) after the current
     * transaction commits. If no transaction is active the message is
     * sent immediately.
     */
    public void publishAlert(AlertResponse alert) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    scopedDelivery.deliverToAuthorizedUsers(ALERT_DESTINATION, alert, alert.endpointId());
                }
            });
        } else {
            scopedDelivery.deliverToAuthorizedUsers(ALERT_DESTINATION, alert, alert.endpointId());
        }
    }
}
