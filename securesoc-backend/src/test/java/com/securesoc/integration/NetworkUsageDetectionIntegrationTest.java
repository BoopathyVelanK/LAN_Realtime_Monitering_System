package com.securesoc.integration;

import com.securesoc.entity.Alert;
import com.securesoc.entity.DetectionRule;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.NetworkUsageEvent;
import com.securesoc.repository.AlertRepository;
import com.securesoc.repository.DetectionRuleRepository;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.repository.NetworkUsageEventRepository;
import com.securesoc.repository.RiskScoreRepository;
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

/**
 * End-to-end: POST /monitoring/network-usage -> persist (REQUIRES_NEW,
 * committed) -> DetectionEngine -> NetworkUsageDetector -> Alert -> RiskScore.
 *
 * Rule under test: NETWORK_USAGE + THRESHOLD, threshold = 1 MiB,
 * window = 300s, severity HIGH (30 risk points per detection).
 *
 * Failure isolation is covered at unit level in MonitoringServiceTest; an
 * integration variant needs its own class (a @MockBean DetectionEngine would
 * replace the real engine for every test here), mirroring
 * UsbDetectionFailureIsolationIntegrationTest.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class NetworkUsageDetectionIntegrationTest {

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
    private NetworkUsageEventRepository networkUsageEventRepository;

    private static final long MIB = 1024L * 1024L;
    private static final int THRESHOLD_MIB = 1;
    private static final int WINDOW_SECONDS = 300;
    private static final SecureRandom RANDOM = new SecureRandom();

    // Unique per test instance (JUnit creates one instance per test method),
    // so a leftover row can never collide on endpoint_devices_agent_token_hash_key.
    private final String agentToken = "it-network-usage-token-" + UUID.randomUUID();

    private final Set<UUID> createdRuleIds = new LinkedHashSet<>();
    private final Set<UUID> createdEndpointIds = new LinkedHashSet<>();

    @Test
    void belowThreshold_noAlertAndNoRiskScore() throws Exception {
        DetectionRule rule = persistRule();
        EndpointDevice endpoint = persistEndpoint();

        postUsage(400_000L, 400_000L, Instant.now());

        assertTrue(alertsFor(endpoint, rule).isEmpty(), "800000 bytes < 1 MiB - no alert expected");
        assertTrue(riskScoreRepository.findByEndpoint_Id(endpoint.getId()).isEmpty(),
            "No detection - no risk score row expected");
        assertEquals(1, eventsFor(endpoint).size(), "The telemetry itself must still be persisted");
    }

    @Test
    void exactlyAtThreshold_createsAlertAndRiskScore() throws Exception {
        DetectionRule rule = persistRule();
        EndpointDevice endpoint = persistEndpoint();

        // 524288 + 524288 = exactly 1 MiB. This single POST is also the
        // persist-before-detect proof: the detector's SUM runs in a separate
        // REQUIRES_NEW transaction and can only reach 1 MiB if it sees the
        // row this very request just committed.
        postUsage(MIB / 2, MIB / 2, Instant.now());

        List<Alert> alerts = alertsFor(endpoint, rule);
        assertEquals(1, alerts.size());
        Alert alert = alerts.get(0);
        assertEquals(Alert.Status.OPEN, alert.getStatus());
        assertEquals(Alert.Severity.HIGH, alert.getSeverity());
        assertEquals(rule.getId(), alert.getRule().getId());
        assertEquals(endpoint.getId(), alert.getEndpoint().getId());

        var risk = riskScoreRepository.findByEndpoint_Id(endpoint.getId());
        assertTrue(risk.isPresent(), "A RiskScore row should exist for this endpoint");
        assertEquals(30, risk.get().getScore(), "One HIGH detection = 30 points");
    }

    @Test
    void aboveThreshold_createsAlertAndRiskScore() throws Exception {
        DetectionRule rule = persistRule();
        EndpointDevice endpoint = persistEndpoint();

        postUsage(2 * MIB, 3 * MIB, Instant.now());

        assertEquals(1, alertsFor(endpoint, rule).size());
        assertEquals(30, riskScoreRepository.findByEndpoint_Id(endpoint.getId()).orElseThrow().getScore());
    }

    @Test
    void rollingWindow_onlySamplesInsideWindowContribute() throws Exception {
        DetectionRule rule = persistRule();
        EndpointDevice endpoint = persistEndpoint();
        Instant now = Instant.now();

        // Far outside the 300s window - must be ignored even though it is huge.
        persistSample(endpoint, 50 * MIB, 50 * MIB, now.minusSeconds(3600));
        // Inside the window: 400 KiB.
        persistSample(endpoint, 200 * 1024L, 200 * 1024L, now.minusSeconds(120));

        // +400 KiB now = 800 KiB in window (< 1 MiB) even though the old
        // sample alone would have crossed the threshold.
        postUsage(200 * 1024L, 200 * 1024L, now);
        assertTrue(alertsFor(endpoint, rule).isEmpty(), "Out-of-window sample must not contribute");

        // +400 KiB more = 1.2 MiB in window -> crosses the threshold.
        postUsage(200 * 1024L, 200 * 1024L, now.plusSeconds(1));
        assertEquals(1, alertsFor(endpoint, rule).size());
    }

    @Test
    void upperBound_futureSamplesRelativeToDetectionTimestampAreIgnored() throws Exception {
        DetectionRule rule = persistRule();
        EndpointDevice endpoint = persistEndpoint();
        Instant now = Instant.now();

        // A sample newer than the event being evaluated (e.g. live data
        // already stored while an older queued sample is replayed).
        persistSample(endpoint, 10 * MIB, 10 * MIB, now.plusSeconds(3600));

        postUsage(100L, 100L, now);

        assertTrue(alertsFor(endpoint, rule).isEmpty(),
            "Samples after occurredAt must not count toward the window");
    }

    @Test
    void nullSampledAt_fallsBackToRecordedAt() throws Exception {
        DetectionRule rule = persistRule();
        EndpointDevice endpoint = persistEndpoint();

        // Legacy row: sampledAt null, recordedAt = now (entity default).
        persistSample(endpoint, MIB, 0L, null);

        // A pre-upgrade agent payload with no sampledAt: detection timestamp
        // falls back to this row's recordedAt as well.
        mockMvc.perform(post("/monitoring/network-usage")
                .servletPath("/monitoring/network-usage")
                .header("X-Agent-Token", agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bytesSent\": 100, \"bytesReceived\": 100, \"interfaceName\": null}"))
            .andExpect(status().isOk());

        assertEquals(1, alertsFor(endpoint, rule).size(),
            "Legacy null-sampledAt row (1 MiB) + new sample must be summed via recordedAt");
    }

    @Test
    void negativeValues_neverReduceTheTotal() throws Exception {
        DetectionRule rule = persistRule();
        EndpointDevice endpoint = persistEndpoint();
        Instant now = Instant.now();

        // network_usage_events has no CHECK constraint, so this can exist.
        persistSample(endpoint, -10 * MIB, -10 * MIB, now.minusSeconds(60));

        // If the negative row were subtracted the total would be -19 MiB.
        // Clamped at zero it contributes nothing, so this 1 MiB sample alone
        // reaches the threshold.
        postUsage(MIB, 0L, now);

        assertEquals(1, alertsFor(endpoint, rule).size());
    }

    @Test
    void repeatedDetections_documentCurrentEndpointDedupBehavior() throws Exception {
        DetectionRule rule = persistRule();
        EndpointDevice endpoint = persistEndpoint();
        Instant now = Instant.now();

        postUsage(MIB, 0L, now);
        postUsage(0L, 0L, now.plusSeconds(64));
        postUsage(0L, 0L, now.plusSeconds(128));

        // Documents CURRENT behavior, not desired behavior: userId is null
        // for endpoint telemetry, so AlertService skips deduplication and
        // every re-detection while the window sum stays >= threshold (here,
        // even from zero-byte samples) creates another OPEN alert and adds
        // another 30 risk points. This test must not be "fixed" by changing
        // dedup as part of this feature.
        assertEquals(3, alertsFor(endpoint, rule).size());
        assertEquals(90, riskScoreRepository.findByEndpoint_Id(endpoint.getId()).orElseThrow().getScore());
    }

    @AfterEach
    void cleanUpCommittedFixtures() {
        alertRepository.findAll().stream()
            .filter(a -> (a.getEndpoint() != null && createdEndpointIds.contains(a.getEndpoint().getId()))
                || (a.getRule() != null && createdRuleIds.contains(a.getRule().getId())))
            .forEach(alertRepository::delete);

        createdEndpointIds.forEach(endpointId ->
            riskScoreRepository.findByEndpoint_Id(endpointId).ifPresent(riskScoreRepository::delete));

        networkUsageEventRepository.findAll().stream()
            .filter(event -> event.getEndpoint() != null && createdEndpointIds.contains(event.getEndpoint().getId()))
            .forEach(networkUsageEventRepository::delete);

        createdEndpointIds.forEach(endpointDeviceRepository::deleteById);
        createdRuleIds.forEach(detectionRuleRepository::deleteById);

        createdRuleIds.clear();
        createdEndpointIds.clear();
    }

    private void postUsage(long bytesSent, long bytesReceived, Instant sampledAt) throws Exception {
        String payload = """
            {
              "bytesSent": %d,
              "bytesReceived": %d,
              "interfaceName": null,
              "sampledAt": "%s"
            }
            """.formatted(bytesSent, bytesReceived, sampledAt);

        mockMvc.perform(post("/monitoring/network-usage")
                .servletPath("/monitoring/network-usage")
                .header("X-Agent-Token", agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());
    }

    private List<Alert> alertsFor(EndpointDevice endpoint, DetectionRule rule) {
        return alertRepository.findAll().stream()
            .filter(a -> a.getEndpoint() != null && endpoint.getId().equals(a.getEndpoint().getId()))
            .filter(a -> a.getRule() != null && rule.getId().equals(a.getRule().getId()))
            .toList();
    }

    private List<NetworkUsageEvent> eventsFor(EndpointDevice endpoint) {
        return networkUsageEventRepository
            .findByEndpoint_IdOrderByRecordedAtDesc(endpoint.getId(), org.springframework.data.domain.Pageable.unpaged())
            .getContent();
    }

    private void persistSample(EndpointDevice endpoint, long sent, long received, Instant sampledAt) {
        NetworkUsageEvent event = new NetworkUsageEvent();
        event.setEndpoint(endpoint);
        event.setBytesSent(sent);
        event.setBytesReceived(received);
        event.setSampledAt(sampledAt);
        networkUsageEventRepository.saveAndFlush(event);
    }

    private DetectionRule persistRule() {
        DetectionRule rule = new DetectionRule();
        rule.setName("IT Network Usage Threshold " + UUID.randomUUID());
        rule.setDescription("Integration-test-only rule for network usage detection; deleted in @AfterEach.");
        rule.setRuleType(DetectionRule.RuleType.THRESHOLD);
        rule.setEventSource("NETWORK_USAGE");
        rule.setThreshold(THRESHOLD_MIB);
        rule.setWindowSeconds(WINDOW_SECONDS);
        rule.setSeverity(DetectionRule.Severity.HIGH);
        rule.setEnabled(true);
        DetectionRule saved = detectionRuleRepository.save(rule);
        createdRuleIds.add(saved.getId());
        return saved;
    }

    private EndpointDevice persistEndpoint() {
        EndpointDevice endpoint = new EndpointDevice();
        endpoint.setHostname("IT-NETWORK-USAGE-ENDPOINT-" + UUID.randomUUID());
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
