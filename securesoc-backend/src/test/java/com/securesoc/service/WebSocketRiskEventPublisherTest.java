package com.securesoc.service;

import com.securesoc.dto.RiskScoreResponse;
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
 * Unit tests for {@link WebSocketRiskEventPublisher}. Same rationale as
 * WebSocketAlertEventPublisherTest - who receives the message is
 * ScopedWebSocketDeliveryTest's job, this only verifies delegation.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketRiskEventPublisherTest {

    @Mock
    private ScopedWebSocketDelivery scopedDelivery;

    private WebSocketRiskEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new WebSocketRiskEventPublisher(scopedDelivery);
    }

    @Test
    void publishRiskScore_delegatesToScopedDeliveryWithCorrectDestinationPayloadAndEndpointId() {
        RiskScoreResponse risk = sampleRisk();

        publisher.publishRiskScore(risk);

        verify(scopedDelivery).deliverToAuthorizedUsers(
            WebSocketRiskEventPublisher.RISK_DESTINATION, risk, risk.endpointId());
    }

    @Test
    void publishRiskScore_destinationIsQueueRisk() {
        publisher.publishRiskScore(sampleRisk());

        verify(scopedDelivery).deliverToAuthorizedUsers(eq("/queue/risk"), any(), any());
    }

    private RiskScoreResponse sampleRisk() {
        return new RiskScoreResponse(UUID.randomUUID(), (short) 65, "HIGH", Instant.now());
    }
}
