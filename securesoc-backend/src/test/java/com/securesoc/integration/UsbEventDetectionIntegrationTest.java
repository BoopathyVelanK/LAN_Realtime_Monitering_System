package com.securesoc.integration;

import com.securesoc.entity.Alert;
import com.securesoc.entity.DetectionRule;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.UsbEvent;
import com.securesoc.repository.AlertRepository;
import com.securesoc.repository.DetectionRuleRepository;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.repository.RiskScoreRepository;
import com.securesoc.repository.UsbEventRepository;
import com.securesoc.security.TokenHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class UsbEventDetectionIntegrationTest {

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
    private UsbEventRepository usbEventRepository;

    private static final int THRESHOLD = 3;
    private static final int WINDOW_SECONDS = 300;
    private static final String AGENT_TOKEN = "test-usb-token-123";

    private final Set<UUID> createdRuleIds = new LinkedHashSet<>();
    private final Set<UUID> createdEndpointIds = new LinkedHashSet<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void thresholdUsbEvents_persistedAndCreateOneAlertAndRiskScore() throws Exception {
        DetectionRule rule = persistRule();
        EndpointDevice endpoint = persistEndpoint();

        long riskScoreCountBefore = riskScoreRepository.count();

        String payload = """
            {
              "deviceName": "SanDisk Ultra",
              "deviceId": "USB\\\\VID_0781&PID_5581",
              "vendorId": "0781",
              "productId": "5581",
              "action": "CONNECTED"
            }
            """;

        for (int i = 0; i < THRESHOLD; i++) {
            mockMvc.perform(post("/monitoring/usb")
                    .servletPath("/monitoring/usb")
                    .header("X-Agent-Token", AGENT_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                .andExpect(status().isOk());
        }

        // Verify the UsbEvents are persisted
        List<UsbEvent> events = usbEventRepository.findByEndpoint_IdOrderByEventTimeDesc(endpoint.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent();
        assertTrue(events.size() >= THRESHOLD, "At least " + THRESHOLD + " UsbEvent rows must survive.");

        // Verify that exactly one Alert is created
        List<Alert> alerts = alertRepository.findAll().stream()
            .filter(a -> a.getEndpoint() != null && endpoint.getId().equals(a.getEndpoint().getId()))
            .filter(a -> a.getRule() != null && rule.getId().equals(a.getRule().getId()))
            .toList();
            
        assertEquals(1, alerts.size(), "Threshold met on the 3rd attempt - exactly one Alert expected");
        assertEquals(Alert.Status.OPEN, alerts.get(0).getStatus());

        // Verify that RiskScore is updated
        assertEquals(riskScoreCountBefore + 1, riskScoreRepository.count(), "A new RiskScore should be created for this endpoint.");
    }

    @AfterEach
    void cleanUpCommittedFixtures() {
        alertRepository.findAll().stream()
            .filter(a -> (a.getEndpoint() != null && createdEndpointIds.contains(a.getEndpoint().getId()))
                || (a.getRule() != null && createdRuleIds.contains(a.getRule().getId())))
            .forEach(alertRepository::delete);

        createdEndpointIds.forEach(endpointId ->
            riskScoreRepository.findByEndpoint_Id(endpointId).ifPresent(riskScoreRepository::delete));

        usbEventRepository.findAll().stream()
            .filter(event -> event.getEndpoint() != null && createdEndpointIds.contains(event.getEndpoint().getId()))
            .forEach(usbEventRepository::delete);

        createdEndpointIds.forEach(endpointDeviceRepository::deleteById);
        createdRuleIds.forEach(detectionRuleRepository::deleteById);

        createdRuleIds.clear();
        createdEndpointIds.clear();
    }

    private DetectionRule persistRule() {
        DetectionRule rule = new DetectionRule();
        rule.setName("IT USB Detection Event Threshold " + UUID.randomUUID());
        rule.setDescription("Integration-test-only rule for USB detection; deleted in @AfterEach.");
        rule.setRuleType(DetectionRule.RuleType.THRESHOLD);
        rule.setEventSource("USB_EVENT");
        rule.setThreshold(THRESHOLD);
        rule.setWindowSeconds(WINDOW_SECONDS);
        rule.setSeverity(DetectionRule.Severity.HIGH);
        rule.setEnabled(true);
        DetectionRule saved = detectionRuleRepository.save(rule);
        createdRuleIds.add(saved.getId());
        return saved;
    }

    private EndpointDevice persistEndpoint() {
        EndpointDevice endpoint = new EndpointDevice();
        endpoint.setHostname("IT-USB-ENDPOINT-" + UUID.randomUUID());
        endpoint.setMacAddress(randomMacAddress());
        endpoint.setAgentTokenHash(TokenHasher.sha256Hex(AGENT_TOKEN));
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
