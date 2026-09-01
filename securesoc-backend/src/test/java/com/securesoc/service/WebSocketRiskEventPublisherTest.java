package com.securesoc.service;

import com.securesoc.dto.RiskScoreResponse;
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
 * Unit tests for {@link WebSocketRiskEventPublisher}.
 *
 * These tests call the publisher directly without a live Spring context or
 * transaction, so publishRiskScore() falls through to the immediate
 * (no-transaction-active) path and sends synchronously - the
 * TransactionSynchronizationManager.isActualTransactionActive() check returns
 * false in a plain Mockito test.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketRiskEventPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private WebSocketRiskEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new WebSocketRiskEventPublisher(messagingTemplate);
    }

    @Test
    void publishRiskScore_sendsToCorrectTopic() {
        RiskScoreResponse risk = sampleRisk();

        publisher.publishRiskScore(risk);

        verify(messagingTemplate).convertAndSend(WebSocketRiskEventPublisher.RISK_TOPIC, risk);
    }

    @Test
    void publishRiskScore_payloadIsPassedThrough() {
        RiskScoreResponse risk = sampleRisk();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        publisher.publishRiskScore(risk);

        verify(messagingTemplate).convertAndSend(
            org.mockito.ArgumentMatchers.eq(WebSocketRiskEventPublisher.RISK_TOPIC),
            payloadCaptor.capture()
        );
        assertEquals(risk, payloadCaptor.getValue());
    }

    @Test
    void publishRiskScore_destinationIsTopicRisk() {
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);

        publisher.publishRiskScore(sampleRisk());

        verify(messagingTemplate).convertAndSend(
            destinationCaptor.capture(),
            org.mockito.ArgumentMatchers.any(Object.class)
        );
        assertEquals("/topic/risk", destinationCaptor.getValue());
    }

    private RiskScoreResponse sampleRisk() {
        return new RiskScoreResponse(UUID.randomUUID(), (short) 65, "HIGH", Instant.now());
    }
}
