package com.securesoc.integration;

import com.securesoc.detection.DetectionContext;
import com.securesoc.detection.DetectionEngine;
import com.securesoc.entity.DetectionRule;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.NetworkUsageEvent;
import com.securesoc.repository.DetectionRuleRepository;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.repository.NetworkUsageEventRepository;
import com.securesoc.security.TokenHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, against a real PostgreSQL transaction, that a failure in
 * post-ingestion detection does not roll back an already-ingested network
 * usage telemetry event. Mirrors UsbDetectionFailureIsolationIntegrationTest.
 *
 * DetectionEngine is mocked here (and only in this class, so it cannot
 * affect NetworkUsageDetectionIntegrationTest) to force the failure
 * deterministically; NetworkUsageEventPersistenceExecutor's commit,
 * DetectionEvaluationExecutor's REQUIRES_NEW boundary and
 * MonitoringService's try/catch all run for real.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class NetworkUsageDetectionFailureIsolationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NetworkUsageEventRepository networkUsageEventRepository;

    @Autowired
    private EndpointDeviceRepository endpointDeviceRepository;

    @Autowired
    private DetectionRuleRepository detectionRuleRepository;

    @MockBean
    private DetectionEngine detectionEngine;

    private static final SecureRandom RANDOM = new SecureRandom();

    // Unique per test instance, so a leftover row can never collide on
    // endpoint_devices_agent_token_hash_key.
    private final String agentToken = "it-network-isolation-token-" + UUID.randomUUID();

    private final Set<UUID> createdEndpointIds = new LinkedHashSet<>();
    private final Set<UUID> createdRuleIds = new LinkedHashSet<>();

    @Test
    void detectionEngineThrows_networkUsageEventIsStillCommitted_andIngestStillReturnsOk() throws Exception {
        EndpointDevice endpoint = persistEndpoint();
        persistRule();

        doThrow(new RuntimeException("simulated detection failure"))
            .when(detectionEngine).evaluate(ArgumentMatchers.any(DetectionContext.class));

        String payload = """
            {
              "bytesSent": 1048576,
              "bytesReceived": 2097152,
              "interfaceName": null,
              "sampledAt": "%s"
            }
            """.formatted(Instant.now());

        // If the detection failure leaked out of recordNetworkUsage(), this
        // would surface as a 5xx - it must not.
        mockMvc.perform(post("/monitoring/network-usage")
                .servletPath("/monitoring/network-usage")
                .header("X-Agent-Token", agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        // A fresh read against the real database proves the row was
        // committed, not flushed and then rolled back.
        List<NetworkUsageEvent> events = networkUsageEventRepository
            .findByEndpoint_IdOrderByRecordedAtDesc(endpoint.getId(), Pageable.unpaged())
            .getContent();

        assertEquals(1, events.size(), "The network usage event must survive a detection engine failure.");
        assertEquals(1048576L, events.get(0).getBytesSent());
        assertEquals(2097152L, events.get(0).getBytesReceived());

        // Detection really was attempted (and was the thing that failed).
        verify(detectionEngine).evaluate(ArgumentMatchers.any(DetectionContext.class));
    }

    @AfterEach
    void cleanUpCommittedFixtures() {
        // Query per created endpoint id rather than walking lazy
        // event.getEndpoint() associations outside a persistence context.
        createdEndpointIds.forEach(endpointId ->
            networkUsageEventRepository
                .findByEndpoint_IdOrderByRecordedAtDesc(endpointId, Pageable.unpaged())
                .getContent()
                .forEach(networkUsageEventRepository::delete));

        createdEndpointIds.forEach(endpointDeviceRepository::deleteById);
        createdRuleIds.forEach(detectionRuleRepository::deleteById);

        createdEndpointIds.clear();
        createdRuleIds.clear();
    }

    private DetectionRule persistRule() {
        DetectionRule rule = new DetectionRule();
        rule.setName("IT Network Isolation Rule " + UUID.randomUUID());
        rule.setDescription("Integration-test-only rule for network usage failure isolation; deleted in @AfterEach.");
        rule.setRuleType(DetectionRule.RuleType.THRESHOLD);
        rule.setEventSource("NETWORK_USAGE");
        rule.setThreshold(1);
        rule.setWindowSeconds(300);
        rule.setSeverity(DetectionRule.Severity.HIGH);
        rule.setEnabled(true);
        DetectionRule saved = detectionRuleRepository.save(rule);
        createdRuleIds.add(saved.getId());
        return saved;
    }

    private EndpointDevice persistEndpoint() {
        EndpointDevice endpoint = new EndpointDevice();
        endpoint.setHostname("IT-NETWORK-ISOLATION-ENDPOINT-" + UUID.randomUUID());
        endpoint.setMacAddress(randomMacAddress());
        endpoint.setAgentTokenHash(TokenHasher.sha256Hex(agentToken));
        EndpointDevice saved = endpointDeviceRepository.save(endpoint);
        createdEndpointIds.add(saved.getId());
        return saved;
    }

    private static String randomMacAddress() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02X", bytes[i]));
            if (i < bytes.length - 1) {
                sb.append(":");
            }
        }
        return sb.toString();
    }
}
