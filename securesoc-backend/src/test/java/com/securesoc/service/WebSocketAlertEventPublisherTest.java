package com.securesoc.service;

import com.securesoc.dto.AlertResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link WebSocketAlertEventPublisher}.
 *
 * These tests call the publisher directly without a live Spring context or
 * transaction, so publishAlert() falls through to the immediate
 * (no-transaction-active) path and sends synchronously - the
 * TransactionSynchronizationManager.isActualTransactionActive() check returns
 * false in a plain Mockito test. That exercises the correct code path for
 * verifying the destination and payload without needing a full Spring context.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketAlertEventPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private WebSocketAlertEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new WebSocketAlertEventPublisher(messagingTemplate);
    }

    @Test
    void publishAlert_sendsToCorrectTopic() {
        AlertResponse alert = sampleAlert();

        publisher.publishAlert(alert);

        verify(messagingTemplate).convertAndSend(WebSocketAlertEventPublisher.ALERT_TOPIC, alert);
    }

    @Test
    void publishAlert_payloadIsPassedThrough() {
        AlertResponse alert = sampleAlert();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        publisher.publishAlert(alert);

        verify(messagingTemplate).convertAndSend(
            org.mockito.ArgumentMatchers.eq(WebSocketAlertEventPublisher.ALERT_TOPIC),
            payloadCaptor.capture()
        );
        assertEquals(alert, payloadCaptor.getValue());
    }

    @Test
    void publishAlert_destinationIsTopicAlerts() {
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);

        publisher.publishAlert(sampleAlert());

        verify(messagingTemplate).convertAndSend(
            destinationCaptor.capture(),
            org.mockito.ArgumentMatchers.any(Object.class)
        );
        assertEquals("/topic/alerts", destinationCaptor.getValue());
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
