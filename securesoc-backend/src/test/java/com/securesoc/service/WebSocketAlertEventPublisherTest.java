package com.securesoc.service;

import com.securesoc.dto.AlertResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link WebSocketAlertEventPublisher}. These tests call
 * the publisher directly without a live Spring context or transaction, so
 * publishAlert() falls through to the immediate (no-transaction-active)
 * path and delegates synchronously - the
 * TransactionSynchronizationManager.isActualTransactionActive() check
 * returns false in a plain Mockito test. Who actually receives the
 * message is ScopedWebSocketDelivery's job and is tested in
 * ScopedWebSocketDeliveryTest, not here - this class only verifies the
 * publisher delegates to it with the right destination/payload/endpointId.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketAlertEventPublisherTest {

    @Mock
    private ScopedWebSocketDelivery scopedDelivery;

    private WebSocketAlertEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new WebSocketAlertEventPublisher(scopedDelivery);
    }

    @Test
    void publishAlert_delegatesToScopedDeliveryWithCorrectDestinationPayloadAndEndpointId() {
        AlertResponse alert = sampleAlert();

        publisher.publishAlert(alert);

        verify(scopedDelivery).deliverToAuthorizedUsers(
            WebSocketAlertEventPublisher.ALERT_DESTINATION, alert, alert.endpointId());
    }

    @Test
    void publishAlert_destinationIsQueueAlerts() {
        publisher.publishAlert(sampleAlert());

        verify(scopedDelivery).deliverToAuthorizedUsers(eq("/queue/alerts"), any(), any());
    }

    private AlertResponse sampleAlert() {
        UUID id = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        return new AlertResponse(
            id, endpointId, "LAB-PC-01", "USB_EVENT",
            "HIGH", "USB threshold exceeded", "3 USB events in 5 min",
            "OPEN", null, null, null, null, null,
            Instant.now(), Instant.now(), null
        );
    }
}
