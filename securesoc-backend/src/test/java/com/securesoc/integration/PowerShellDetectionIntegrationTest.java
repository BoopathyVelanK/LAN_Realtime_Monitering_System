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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, against a real PostgreSQL container (not mocked repositories),
 * that:
 *   1. a running-app snapshot POST persists commandLine alongside the
 *      existing processName/windowTitle/pid fields;
 *   2. PowerShellDetector, running in DetectionEvaluationExecutor's
 *      separate REQUIRES_NEW transaction, can see those same-request rows
 *      under PostgreSQL READ COMMITTED - the same persistence-before-
 *      detection ordering proven for PROCESS_MATCH by
 *      SuspiciousProcessDetectionIntegrationTest, which this test follows;
 *   3. a POWERSHELL_MATCH rule (processName + commandPattern) produces a
 *      detected Alert only when both the process name (exact,
 *      case-insensitive) and the command pattern (substring,
 *      case-insensitive) match, and that PROCESS_MATCH/POWERSHELL_MATCH
 *      never collide (see PowerShellDetector's Javadoc on
 *      AmbiguousDetectorException).
 *
 * This exercises the real DetectionEngine/PowerShellDetector/AlertService
 * chain rather than mocking DetectionEngine, for the same reasons given in
 * SuspiciousProcessDetectionIntegrationTest's Javadoc.
 *
 * Lazy-loading note: identical discipline to
 * SuspiciousProcessDetectionIntegrationTest - only identifier (getId())
 * access on a lazy proxy is used outside of an active session; RunningApp's
 * commandLine itself is read only through fresh repository queries, never
 * by dereferencing a detached RunningAppSnapshot.getApps().
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PowerShellDetectionIntegrationTest {

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

    private static final String AGENT_TOKEN_PREFIX = "test-powershell-token-";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Set<UUID> createdEndpointIds = new LinkedHashSet<>();
    private final Set<UUID> createdRuleIds = new LinkedHashSet<>();

    @Test
    void encodedCommandLine_isPersisted_detectedAcrossTheRealTransactionBoundary_andProducesAnAlert() throws Exception {
        String agentToken = uniqueAgentToken();
        EndpointDevice endpoint = persistEndpoint(agentToken);
        DetectionRule rule = persistRule("powershell.exe", "-encodedcommand");

        String payload = """
            {
              "applications": [
                {"processName": "powershell.exe", "windowTitle": null, "pid": 4242, "commandLine": "powershell.exe -EncodedCommand ABC"},
                {"processName": "explorer.exe", "windowTitle": null, "pid": 1000, "commandLine": null}
              ]
            }
            """;

        mockMvc.perform(post("/monitoring/running-apps")
                .servletPath("/monitoring/running-apps")
                .header("X-Agent-Token", agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        // Decisive assertion #1: both RunningApp rows were persisted and
        // committed, with commandLine carried through (or left null).
        List<RunningAppSnapshot> snapshots = runningAppSnapshotRepository
            .findByEndpoint_IdOrderByCapturedAtDesc(endpoint.getId(), Pageable.unpaged())
            .getContent();
        assertEquals(1, snapshots.size(), "The running-app snapshot must survive ingestion.");
        UUID snapshotId = snapshots.get(0).getId();

        long persistedAppCount = runningAppRepository.findAll().stream()
            .filter(a -> a.getSnapshot() != null && snapshotId.equals(a.getSnapshot().getId()))
            .count();
        assertEquals(2, persistedAppCount, "Both RunningApp rows must have been persisted and committed.");

        boolean commandLinePersisted = runningAppRepository.findAll().stream()
            .anyMatch(a -> a.getSnapshot() != null && snapshotId.equals(a.getSnapshot().getId())
                && "powershell.exe -EncodedCommand ABC".equals(a.getCommandLine()));
        assertTrue(commandLinePersisted, "commandLine must be persisted on the RunningApp row.");

        // Decisive assertion #2: PowerShellDetector, evaluated in
        // DetectionEvaluationExecutor's own REQUIRES_NEW transaction,
        // could see the just-committed RunningApp rows and produced a
        // real Alert row via the same AlertService path every other
        // detector uses - no mocking of DetectionEngine, PowerShellDetector,
        // AlertService, or RiskScoreService in this test.
        List<Alert> alerts = alertRepository.findAll().stream()
            .filter(a -> a.getRule() != null && rule.getId().equals(a.getRule().getId()))
            .toList();
        assertEquals(1, alerts.size(), "A matching process + command pattern must produce exactly one alert.");
        assertEquals(Alert.Status.OPEN, alerts.get(0).getStatus());
    }

    @Test
    void benignPowerShellCommandLine_doesNotMatchConfiguredPattern_noAlertProduced() throws Exception {
        String agentToken = uniqueAgentToken();
        persistEndpoint(agentToken);
        DetectionRule rule = persistRule("powershell.exe", "-encodedcommand");

        String payload = """
            {
              "applications": [
                {"processName": "powershell.exe", "windowTitle": null, "pid": 4242, "commandLine": "powershell.exe -NoProfile"}
              ]
            }
            """;

        mockMvc.perform(post("/monitoring/running-apps")
                .servletPath("/monitoring/running-apps")
                .header("X-Agent-Token", agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        List<Alert> alerts = alertRepository.findAll().stream()
            .filter(a -> a.getRule() != null && rule.getId().equals(a.getRule().getId()))
            .toList();
        assertTrue(alerts.isEmpty(), "A benign command line without the configured pattern must not produce an alert.");
    }

    @Test
    void caseInsensitiveProcessNameAndCommandPattern_stillDetects() throws Exception {
        String agentToken = uniqueAgentToken();
        persistEndpoint(agentToken);
        DetectionRule rule = persistRule("powershell.exe", "-encodedcommand");

        String payload = """
            {
              "applications": [
                {"processName": "PowerShell.EXE", "windowTitle": null, "pid": 4242, "commandLine": "powershell.exe -ENCODEDCOMMAND ABC"}
              ]
            }
            """;

        mockMvc.perform(post("/monitoring/running-apps")
                .servletPath("/monitoring/running-apps")
                .header("X-Agent-Token", agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        List<Alert> alerts = alertRepository.findAll().stream()
            .filter(a -> a.getRule() != null && rule.getId().equals(a.getRule().getId()))
            .toList();
        assertEquals(1, alerts.size(),
            "POWERSHELL_MATCH must be case-insensitive on both processName and commandPattern.");
    }

    @Test
    void nearMissProcessName_doesNotMatch_noAlertProduced() throws Exception {
        String agentToken = uniqueAgentToken();
        persistEndpoint(agentToken);
        DetectionRule rule = persistRule("powershell.exe", "-encodedcommand");

        String payload = """
            {
              "applications": [
                {"processName": "powershell-helper.exe", "windowTitle": null, "pid": 4242, "commandLine": "powershell-helper.exe -EncodedCommand ABC"}
              ]
            }
            """;

        mockMvc.perform(post("/monitoring/running-apps")
                .servletPath("/monitoring/running-apps")
                .header("X-Agent-Token", agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        List<Alert> alerts = alertRepository.findAll().stream()
            .filter(a -> a.getRule() != null && rule.getId().equals(a.getRule().getId()))
            .toList();
        assertTrue(alerts.isEmpty(),
            "A near-miss process name (powershell-helper.exe) must not match an exact rule for powershell.exe.");
    }

    @Test
    void nearMissCommandContainingConfiguredSubstring_stillMatches_perLockedSubstringSemantics() throws Exception {
        // Locked design: commandPattern is a plain substring match, with no
        // word-boundary logic - "-EncodedCommandSomethingElse" contains
        // "-encodedcommand" as a substring and must therefore match.
        String agentToken = uniqueAgentToken();
        persistEndpoint(agentToken);
        DetectionRule rule = persistRule("powershell.exe", "-encodedcommand");

        String payload = """
            {
              "applications": [
                {"processName": "powershell.exe", "windowTitle": null, "pid": 4242, "commandLine": "powershell.exe -EncodedCommandSomethingElse"}
              ]
            }
            """;

        mockMvc.perform(post("/monitoring/running-apps")
                .servletPath("/monitoring/running-apps")
                .header("X-Agent-Token", agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        List<Alert> alerts = alertRepository.findAll().stream()
            .filter(a -> a.getRule() != null && rule.getId().equals(a.getRule().getId()))
            .toList();
        assertEquals(1, alerts.size(),
            "Plain substring matching must match even when the pattern is only part of a longer flag.");
    }

    @Test
    void missingCommandLine_fromOlderAgent_neverMatches_noAlertProduced() throws Exception {
        // Backward compatibility: an older agent that sends no commandLine
        // at all must not crash the ingest, and a POWERSHELL_MATCH rule
        // must simply never match a null commandLine.
        String agentToken = uniqueAgentToken();
        persistEndpoint(agentToken);
        DetectionRule rule = persistRule("powershell.exe", "-encodedcommand");

        String payload = """
            {
              "applications": [
                {"processName": "powershell.exe", "windowTitle": null, "pid": 4242}
              ]
            }
            """;

        mockMvc.perform(post("/monitoring/running-apps")
                .servletPath("/monitoring/running-apps")
                .header("X-Agent-Token", agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        List<Alert> alerts = alertRepository.findAll().stream()
            .filter(a -> a.getRule() != null && rule.getId().equals(a.getRule().getId()))
            .toList();
        assertTrue(alerts.isEmpty(), "A null commandLine (older agent) must never match a POWERSHELL_MATCH rule.");
    }

    @AfterEach
    void cleanUpCommittedFixtures() {
        alertRepository.findAll().stream()
            .filter(a -> a.getRule() != null && createdRuleIds.contains(a.getRule().getId()))
            .forEach(alertRepository::delete);

        Set<UUID> snapshotIds = createdEndpointIds.stream()
            .flatMap(id -> runningAppSnapshotRepository
                .findByEndpoint_IdOrderByCapturedAtDesc(id, Pageable.unpaged())
                .stream())
            .map(RunningAppSnapshot::getId)
            .collect(Collectors.toSet());

        runningAppRepository.findAll().stream()
            .filter(a -> a.getSnapshot() != null && snapshotIds.contains(a.getSnapshot().getId()))
            .forEach(runningAppRepository::delete);

        runningAppSnapshotRepository.findAll().stream()
            .filter(s -> snapshotIds.contains(s.getId()))
            .forEach(runningAppSnapshotRepository::delete);

        createdRuleIds.forEach(detectionRuleRepository::deleteById);
        createdEndpointIds.forEach(endpointDeviceRepository::deleteById);
        createdRuleIds.clear();
        createdEndpointIds.clear();
    }

    private static String uniqueAgentToken() {
        return AGENT_TOKEN_PREFIX + UUID.randomUUID();
    }

    private EndpointDevice persistEndpoint(String agentToken) {
        EndpointDevice endpoint = new EndpointDevice();
        endpoint.setHostname("IT-POWERSHELL-ENDPOINT-" + UUID.randomUUID());
        endpoint.setMacAddress(randomMacAddress());
        endpoint.setAgentTokenHash(TokenHasher.sha256Hex(agentToken));
        EndpointDevice saved = endpointDeviceRepository.save(endpoint);
        createdEndpointIds.add(saved.getId());
        return saved;
    }

    private DetectionRule persistRule(String processName, String commandPattern) {
        DetectionRule rule = new DetectionRule();
        rule.setName("PowerShell match: " + processName + " " + UUID.randomUUID());
        rule.setRuleType(DetectionRule.RuleType.POWERSHELL_MATCH);
        rule.setEventSource("RUNNING_APP");
        rule.setProcessName(processName);
        rule.setCommandPattern(commandPattern);
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
