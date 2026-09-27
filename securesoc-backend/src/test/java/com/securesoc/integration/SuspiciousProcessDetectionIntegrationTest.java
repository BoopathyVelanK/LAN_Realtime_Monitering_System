package com.securesoc.integration;

import com.securesoc.entity.Alert;
import com.securesoc.entity.DetectionRule;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.RunningAppSnapshot;
import com.securesoc.repository.AlertRepository;
import com.securesoc.repository.DetectionRuleRepository;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.repository.RunningAppRepository;
import com.securesoc.repository.RunningAppSnapshotRepository;
import com.securesoc.security.TokenHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, against a real PostgreSQL container (not mocked repositories),
 * that:
 *   1. a running-app snapshot POST persists both the RunningAppSnapshot
 *      and its RunningApp rows;
 *   2. SuspiciousProcessDetector, running in DetectionEvaluationExecutor's
 *      separate REQUIRES_NEW transaction, can see those same-request rows
 *      under PostgreSQL READ COMMITTED - the persistence-before-detection
 *      ordering RunningAppPersistenceExecutor exists to guarantee (see its
 *      Javadoc, and UsbDetectionFailureIsolationIntegrationTest /
 *      VpnEventDetectionIntegrationTest for the established precedent this
 *      test follows);
 *   3. an exact, case-insensitive process-name match produces a detected
 *      Alert, and a similarly-named-but-not-exact process does not.
 *
 * This exercises the real DetectionEngine/SuspiciousProcessDetector/
 * AlertService chain rather than mocking DetectionEngine, since the
 * matching behaviour itself (case-insensitivity, exactness) is exactly
 * what needs proving against a real database - not just the transaction
 * boundary (which UsbDetectionFailureIsolationIntegrationTest already
 * covers generically for the persistence-executor pattern).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class SuspiciousProcessDetectionIntegrationTest {

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
    private RunningAppSnapshotRepository runningAppSnapshotRepository;

    @Autowired
    private RunningAppRepository runningAppRepository;

    @Autowired
    private EndpointDeviceRepository endpointDeviceRepository;

    @Autowired
    private DetectionRuleRepository detectionRuleRepository;

    @Autowired
    private AlertRepository alertRepository;

    private static final String AGENT_TOKEN = "test-suspicious-process-token-123";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Set<UUID> createdEndpointIds = new LinkedHashSet<>();
    private final Set<UUID> createdRuleIds = new LinkedHashSet<>();

    @Test
    void matchingProcess_isPersisted_detectedAcrossTheRealTransactionBoundary_andProducesAnAlert() throws Exception {
        EndpointDevice endpoint = persistEndpoint();
        DetectionRule rule = persistRule("powershell.exe");

        String payload = """
            {
              "applications": [
                {"processName": "powershell.exe", "windowTitle": null, "pid": 4242},
                {"processName": "explorer.exe", "windowTitle": null, "pid": 1000}
              ]
            }
            """;

        mockMvc.perform(post("/monitoring/running-apps")
                .servletPath("/monitoring/running-apps")
                .header("X-Agent-Token", AGENT_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        // Decisive assertion #1: both the snapshot and its RunningApp rows
        // were actually committed - a fresh read against the real
        // database, not the same persistence context that performed the
        // write.
        List<RunningAppSnapshot> snapshots = runningAppSnapshotRepository
            .findByEndpoint_IdOrderByCapturedAtDesc(endpoint.getId(), Pageable.unpaged())
            .getContent();
        assertEquals(1, snapshots.size(), "The running-app snapshot must survive ingestion.");
        assertEquals(2, snapshots.get(0).getApps().size());

        // Decisive assertion #2: SuspiciousProcessDetector, evaluated in
        // DetectionEvaluationExecutor's own REQUIRES_NEW transaction,
        // could see the just-committed RunningApp rows (proving the
        // RunningAppPersistenceExecutor ordering) and produced a real
        // Alert row via the same AlertService path every other detector
        // uses - no mocking of DetectionEngine in this test.
        List<Alert> alerts = alertRepository.findAll().stream()
            .filter(a -> a.getRule() != null && rule.getId().equals(a.getRule().getId()))
            .toList();
        assertEquals(1, alerts.size(), "A matching process must produce exactly one alert.");
        assertEquals(Alert.Status.OPEN, alerts.get(0).getStatus());
    }

    @Test
    void similarButNotExactProcessName_doesNotMatch_noAlertProduced() throws Exception {
        persistEndpoint();
        DetectionRule rule = persistRule("powershell.exe");

        String payload = """
            {
              "applications": [
                {"processName": "powershell_ise.exe", "windowTitle": null, "pid": 4242},
                {"processName": "powershell.exe.tmp", "windowTitle": null, "pid": 4243}
              ]
            }
            """;

        mockMvc.perform(post("/monitoring/running-apps")
                .servletPath("/monitoring/running-apps")
                .header("X-Agent-Token", AGENT_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        List<Alert> alerts = alertRepository.findAll().stream()
            .filter(a -> a.getRule() != null && rule.getId().equals(a.getRule().getId()))
            .toList();
        assertTrue(alerts.isEmpty(), "A near-miss process name must not match an exact PROCESS_MATCH rule.");
    }

    @Test
    void caseInsensitiveMatch_stillDetects() throws Exception {
        persistEndpoint();
        DetectionRule rule = persistRule("powershell.exe");

        String payload = """
            {
              "applications": [
                {"processName": "POWERSHELL.EXE", "windowTitle": null, "pid": 4242}
              ]
            }
            """;

        mockMvc.perform(post("/monitoring/running-apps")
                .servletPath("/monitoring/running-apps")
                .header("X-Agent-Token", AGENT_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        List<Alert> alerts = alertRepository.findAll().stream()
            .filter(a -> a.getRule() != null && rule.getId().equals(a.getRule().getId()))
            .toList();
        assertEquals(1, alerts.size(), "PROCESS_MATCH must be case-insensitive.");
    }

    @AfterEach
    void cleanUpCommittedFixtures() {
        alertRepository.findAll().stream()
            .filter(a -> a.getRule() != null && createdRuleIds.contains(a.getRule().getId()))
            .forEach(alertRepository::delete);

        runningAppRepository.findAll().stream()
            .filter(a -> a.getSnapshot() != null
                && a.getSnapshot().getEndpoint() != null
                && createdEndpointIds.contains(a.getSnapshot().getEndpoint().getId()))
            .forEach(runningAppRepository::delete);

        runningAppSnapshotRepository.findAll().stream()
            .filter(s -> s.getEndpoint() != null && createdEndpointIds.contains(s.getEndpoint().getId()))
            .forEach(runningAppSnapshotRepository::delete);

        createdRuleIds.forEach(detectionRuleRepository::deleteById);
        createdEndpointIds.forEach(endpointDeviceRepository::deleteById);
        createdRuleIds.clear();
        createdEndpointIds.clear();
    }

    private EndpointDevice persistEndpoint() {
        EndpointDevice endpoint = new EndpointDevice();
        endpoint.setHostname("IT-SUSPICIOUS-PROCESS-ENDPOINT-" + UUID.randomUUID());
        endpoint.setMacAddress(randomMacAddress());
        endpoint.setAgentTokenHash(TokenHasher.sha256Hex(AGENT_TOKEN));
        EndpointDevice saved = endpointDeviceRepository.save(endpoint);
        createdEndpointIds.add(saved.getId());
        return saved;
    }

    private DetectionRule persistRule(String processName) {
        DetectionRule rule = new DetectionRule();
        rule.setName("Suspicious process: " + processName + " " + UUID.randomUUID());
        rule.setRuleType(DetectionRule.RuleType.PROCESS_MATCH);
        rule.setEventSource("RUNNING_APP");
        rule.setProcessName(processName);
        rule.setSeverity(DetectionRule.Severity.HIGH);
        rule.setEnabled(true);
        DetectionRule saved = detectionRuleRepository.save(rule);
        createdRuleIds.add(saved.getId());
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
