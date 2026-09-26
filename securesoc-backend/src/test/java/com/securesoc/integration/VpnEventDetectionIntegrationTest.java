package com.securesoc.integration;

import com.securesoc.detection.DetectionContext;
import com.securesoc.detection.DetectionEngine;
import com.securesoc.entity.Alert;
import com.securesoc.entity.DetectionRule;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.VpnEvent;
import com.securesoc.repository.AlertRepository;
import com.securesoc.repository.DetectionRuleRepository;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.repository.RiskScoreRepository;
import com.securesoc.repository.VpnEventRepository;
import com.securesoc.security.TokenHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the complete VPN ingestion -> persistence -> DetectionEngine ->
 * VpnEventDetector -> AlertService/RiskScoreService path, following
 * UsbEventDetectionIntegrationTest's established conventions.
 *
 * The detection-failure-isolation guarantee (mirroring
 * UsbDetectionFailureIsolationIntegrationTest, which uses a @MockBean in
 * its own separate test class) is folded into this same file as a third
 * test method instead of a second file, using a @SpyBean DetectionEngine
 * that is reset to its real, delegating behavior in @AfterEach - this
 * lets the other two tests below exercise the real DetectionEngine/
 * VpnEventDetector/AlertService/RiskScoreService wiring while the
 * isolation test alone forces a failure via Mockito's doThrow.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class VpnEventDetectionIntegrationTest {

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
    private AlertRepository alertRepository;

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Autowired
    private DetectionRuleRepository detectionRuleRepository;

    @Autowired
    private EndpointDeviceRepository endpointDeviceRepository;

    @Autowired
    private VpnEventRepository vpnEventRepository;

    @SpyBean
    private DetectionEngine detectionEngine;

    private static final int THRESHOLD = 3;
    private static final int WINDOW_SECONDS = 300;
    private static final String AGENT_TOKEN_ACTIVE = "test-vpn-active-token-123";
    private static final String AGENT_TOKEN_INACTIVE = "test-vpn-inactive-token-123";
    private static final String AGENT_TOKEN_ISOLATION = "test-vpn-isolation-token-123";

    private final Set<UUID> createdRuleIds = new LinkedHashSet<>();
    private final Set<UUID> createdEndpointIds = new LinkedHashSet<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void thresholdActiveVpnEvents_persistedAndCreateOneAlertAndRiskScore() throws Exception {
        DetectionRule rule = persistRule();
        EndpointDevice endpoint = persistEndpoint("IT-VPN-ACTIVE-ENDPOINT-", AGENT_TOKEN_ACTIVE);

        String payload = """
            {
              "adapterName": "tap0",
              "active": true
            }
            """;

        for (int i = 0; i < THRESHOLD; i++) {
            mockMvc.perform(post("/monitoring/vpn")
                    .servletPath("/monitoring/vpn")
                    .header("X-Agent-Token", AGENT_TOKEN_ACTIVE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                .andExpect(status().isOk());
        }

        // Verify the VpnEvents are persisted
        List<VpnEvent> events = vpnEventRepository
            .findByEndpoint_IdOrderByDetectedAtDesc(endpoint.getId(), Pageable.unpaged())
            .getContent();
        assertTrue(events.size() >= THRESHOLD, "At least " + THRESHOLD + " VpnEvent rows must survive.");

        // Verify that exactly one Alert is created
        List<Alert> alerts = alertRepository.findAll().stream()
            .filter(a -> a.getEndpoint() != null && endpoint.getId().equals(a.getEndpoint().getId()))
            .filter(a -> a.getRule() != null && rule.getId().equals(a.getRule().getId()))
            .toList();

        assertEquals(1, alerts.size(), "Threshold met on the 3rd active VPN event - exactly one Alert expected");
        Alert alert = alerts.get(0);
        assertEquals(Alert.Status.OPEN, alert.getStatus());
        assertEquals(Alert.Severity.HIGH, alert.getSeverity(), "Alert severity must match the DetectionRule");
        assertEquals(rule.getId(), alert.getRule().getId(), "Alert must reference the correct rule");
        assertEquals(endpoint.getId(), alert.getEndpoint().getId(), "Alert must reference the correct endpoint");

        // Verify that RiskScore is updated appropriately
        var riskScoreOpt = riskScoreRepository.findByEndpoint_Id(endpoint.getId());
        assertTrue(riskScoreOpt.isPresent(), "A new RiskScore should be created for this endpoint.");
        var riskScore = riskScoreOpt.get();
        assertEquals(endpoint.getId(), riskScore.getEndpoint().getId(), "RiskScore must reference the correct endpoint");
        assertEquals(30, riskScore.getScore(), "Score should be 30 for one HIGH severity detection");
    }

    @Test
    void inactiveVpnEvents_areNeverCountedTowardTheThreshold() throws Exception {
        DetectionRule rule = persistRule();
        EndpointDevice endpoint = persistEndpoint("IT-VPN-INACTIVE-ENDPOINT-", AGENT_TOKEN_INACTIVE);

        String payload = """
            {
              "adapterName": null,
              "active": false
            }
            """;

        for (int i = 0; i < THRESHOLD; i++) {
            mockMvc.perform(post("/monitoring/vpn")
                    .servletPath("/monitoring/vpn")
                    .header("X-Agent-Token", AGENT_TOKEN_INACTIVE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                .andExpect(status().isOk());
        }

        // Inactive VPN telemetry is still persisted - it just must never
        // count toward the active-only detection threshold.
        List<VpnEvent> events = vpnEventRepository
            .findByEndpoint_IdOrderByDetectedAtDesc(endpoint.getId(), Pageable.unpaged())
            .getContent();
        assertTrue(events.size() >= THRESHOLD, "Inactive VpnEvent rows must still be persisted.");

        List<Alert> alerts = alertRepository.findAll().stream()
            .filter(a -> a.getEndpoint() != null && endpoint.getId().equals(a.getEndpoint().getId()))
            .filter(a -> a.getRule() != null && rule.getId().equals(a.getRule().getId()))
            .toList();

        assertEquals(0, alerts.size(), "Inactive VPN events must never trigger a detection/Alert");
        assertTrue(riskScoreRepository.findByEndpoint_Id(endpoint.getId()).isEmpty(),
            "No RiskScore should be created when no detection occurred");
    }

    /**
     * Mirrors UsbDetectionFailureIsolationIntegrationTest's guarantee for
     * VPN: a failure in post-ingestion detection processing must not roll
     * back an already-ingested VPN telemetry event. See this class's
     * javadoc for why a @SpyBean (reset in @AfterEach) is used here
     * instead of a second, @MockBean-based test file.
     */
    @Test
    void detectionEngineThrows_vpnEventIsStillCommitted_andIngestStillReturnsOk() throws Exception {
        EndpointDevice endpoint = persistEndpoint("IT-VPN-ISOLATION-ENDPOINT-", AGENT_TOKEN_ISOLATION);

        doThrow(new RuntimeException("simulated detection failure"))
            .when(detectionEngine).evaluate(ArgumentMatchers.any(DetectionContext.class));

        String payload = """
            {
              "adapterName": "tap0",
              "active": true
            }
            """;

        // If recordVpn()'s persistence were rolled back by the spied
        // DetectionEngine's exception, this request would surface as a
        // 5xx - it must not.
        mockMvc.perform(post("/monitoring/vpn")
                .servletPath("/monitoring/vpn")
                .header("X-Agent-Token", AGENT_TOKEN_ISOLATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        // The real, decisive assertion: querying a fresh read against the
        // real database proves the VpnEvent row was actually committed,
        // not merely flushed-then-rolled-back.
        List<VpnEvent> events = vpnEventRepository
            .findByEndpoint_IdOrderByDetectedAtDesc(endpoint.getId(), Pageable.unpaged())
            .getContent();

        assertEquals(1, events.size(), "The VPN telemetry event must survive a detection engine failure.");
        assertEquals("tap0", events.get(0).getAdapterName());
    }

    @AfterEach
    void cleanUpCommittedFixtures() {
        // Restores the spy's default (real, delegating) behavior so a
        // doThrow() stubbed in the isolation test above never leaks into
        // the other tests in this class.
        Mockito.reset(detectionEngine);

        alertRepository.findAll().stream()
            .filter(a -> (a.getEndpoint() != null && createdEndpointIds.contains(a.getEndpoint().getId()))
                || (a.getRule() != null && createdRuleIds.contains(a.getRule().getId())))
            .forEach(alertRepository::delete);

        createdEndpointIds.forEach(endpointId ->
            riskScoreRepository.findByEndpoint_Id(endpointId).ifPresent(riskScoreRepository::delete));

        vpnEventRepository.findAll().stream()
            .filter(event -> event.getEndpoint() != null && createdEndpointIds.contains(event.getEndpoint().getId()))
            .forEach(vpnEventRepository::delete);

        createdEndpointIds.forEach(endpointDeviceRepository::deleteById);
        createdRuleIds.forEach(detectionRuleRepository::deleteById);

        createdRuleIds.clear();
        createdEndpointIds.clear();
    }

    private DetectionRule persistRule() {
        DetectionRule rule = new DetectionRule();
        rule.setName("IT VPN Detection Event Threshold " + UUID.randomUUID());
        rule.setDescription("Integration-test-only rule for VPN detection; deleted in @AfterEach.");
        rule.setRuleType(DetectionRule.RuleType.THRESHOLD);
        rule.setEventSource("VPN_EVENT");
        rule.setThreshold(THRESHOLD);
        rule.setWindowSeconds(WINDOW_SECONDS);
        rule.setSeverity(DetectionRule.Severity.HIGH);
        rule.setEnabled(true);
        DetectionRule saved = detectionRuleRepository.save(rule);
        createdRuleIds.add(saved.getId());
        return saved;
    }

    private EndpointDevice persistEndpoint(String hostnamePrefix, String agentToken) {
        EndpointDevice endpoint = new EndpointDevice();
        endpoint.setHostname(hostnamePrefix + UUID.randomUUID());
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
