package com.securesoc.integration;

import com.securesoc.config.AgentProperties;
import com.securesoc.dto.agent.AgentRegisterRequest;
import com.securesoc.dto.agent.AgentRegisterResponse;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.security.TokenHasher;
import com.securesoc.service.AgentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stable endpoint identity: AgentService.register() must recognise the same
 * machine by deviceId (Windows MachineGuid) first, then by MAC (legacy
 * agents / legacy rows), and only create a new EndpointDevice when neither
 * matches. Runs against real PostgreSQL so the V14 unique index is exercised
 * (see UsbEventDetectionIntegrationTest for why these tests are not
 * @Transactional: fixtures are committed and removed in @AfterEach).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AgentRegistrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentProperties agentProperties;

    @Autowired
    private EndpointDeviceRepository endpointDeviceRepository;

    @Autowired
    private MockMvc mockMvc;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Set<UUID> createdEndpointIds = new LinkedHashSet<>();

    // 1. First registration with deviceId creates exactly one EndpointDevice.
    @Test
    void firstRegistration_withDeviceId_createsExactlyOneDevice() {
        String deviceId = newDeviceId();
        String mac = randomMacAddress();

        AgentRegisterResponse response = register(request(deviceId, mac, "10.0.0.10", "LAB-PC-01"));

        EndpointDevice device = endpointDeviceRepository.findById(response.endpointId()).orElseThrow();
        assertEquals(deviceId, device.getDeviceId());
        assertEquals(mac, device.getMacAddress());
        assertEquals("LAB-PC-01", device.getHostname());
        assertEquals(1, countByDeviceId(deviceId));
    }

    // 2. Same deviceId registers again: same row, no duplicate, token rotated onto the same row.
    @Test
    void sameDeviceId_registersAgain_reusesSameDevice() {
        String deviceId = newDeviceId();
        String mac = randomMacAddress();

        AgentRegisterResponse first = register(request(deviceId, mac, "10.0.0.10", "LAB-PC-01"));
        AgentRegisterResponse second = register(request(deviceId, mac, "10.0.0.10", "LAB-PC-01"));

        assertEquals(first.endpointId(), second.endpointId());
        assertEquals(1, countByDeviceId(deviceId));
        assertTrue(endpointDeviceRepository.findByAgentTokenHash(TokenHasher.sha256Hex(first.agentToken())).isEmpty(),
            "The previous token must be invalidated by the re-registration");
        assertEquals(first.endpointId(),
            endpointDeviceRepository.findByAgentTokenHash(TokenHasher.sha256Hex(second.agentToken()))
                .orElseThrow().getId());
    }

    // 3. Same deviceId, changed IP: same row, IP updated.
    @Test
    void sameDeviceId_changedIp_updatesSameDevice() {
        String deviceId = newDeviceId();
        String mac = randomMacAddress();

        AgentRegisterResponse first = register(request(deviceId, mac, "10.0.0.10", "LAB-PC-01"));
        AgentRegisterResponse second = register(request(deviceId, mac, "192.168.1.55", "LAB-PC-01"));

        assertEquals(first.endpointId(), second.endpointId());
        assertEquals("192.168.1.55", endpointDeviceRepository.findById(first.endpointId()).orElseThrow().getIpAddress());
        assertEquals(1, countByDeviceId(deviceId));
    }

    // 4. Same deviceId, changed MAC (e.g. Windows picked another adapter): same row, MAC updated.
    @Test
    void sameDeviceId_changedMac_updatesSameDevice() {
        String deviceId = newDeviceId();
        String oldMac = randomMacAddress();
        String newMac = randomMacAddress();

        AgentRegisterResponse first = register(request(deviceId, oldMac, "10.0.0.10", "LAB-PC-01"));
        AgentRegisterResponse second = register(request(deviceId, newMac, "10.0.0.10", "LAB-PC-01"));

        assertEquals(first.endpointId(), second.endpointId());
        assertEquals(newMac, endpointDeviceRepository.findById(first.endpointId()).orElseThrow().getMacAddress());
        assertEquals(1, countByDeviceId(deviceId));
        assertTrue(endpointDeviceRepository.findByMacAddress(oldMac).isEmpty(),
            "The old MAC must not be left behind on a second row");
    }

    // 5. Same deviceId, changed hostname: same row, hostname updated.
    @Test
    void sameDeviceId_changedHostname_updatesSameDevice() {
        String deviceId = newDeviceId();
        String mac = randomMacAddress();

        AgentRegisterResponse first = register(request(deviceId, mac, "10.0.0.10", "OLD-NAME"));
        AgentRegisterResponse second = register(request(deviceId, mac, "10.0.0.10", "NEW-NAME"));

        assertEquals(first.endpointId(), second.endpointId());
        assertEquals("NEW-NAME", endpointDeviceRepository.findById(first.endpointId()).orElseThrow().getHostname());
        assertEquals(1, countByDeviceId(deviceId));
    }

    // 6. A genuinely different deviceId creates a separate device, even with the same hostname.
    @Test
    void differentDeviceId_createsSeparateDevice() {
        String hostname = "SHARED-HOSTNAME-" + UUID.randomUUID();

        AgentRegisterResponse first = register(request(newDeviceId(), randomMacAddress(), "10.0.0.10", hostname));
        AgentRegisterResponse second = register(request(newDeviceId(), randomMacAddress(), "10.0.0.11", hostname));

        assertNotEquals(first.endpointId(), second.endpointId());
        assertTrue(endpointDeviceRepository.existsById(first.endpointId()));
        assertTrue(endpointDeviceRepository.existsById(second.endpointId()));
    }

    // 7. Legacy agent (no deviceId) falls back to MAC lookup and leaves device_id NULL.
    @Test
    void legacyRegistration_withoutDeviceId_fallsBackToMac() {
        String mac = randomMacAddress();

        AgentRegisterResponse first = register(request(null, mac, "10.0.0.10", "LEGACY-PC"));
        AgentRegisterResponse second = register(request(null, mac, "10.0.0.20", "LEGACY-PC"));

        assertEquals(first.endpointId(), second.endpointId());
        EndpointDevice device = endpointDeviceRepository.findById(first.endpointId()).orElseThrow();
        assertNull(device.getDeviceId());
        assertEquals("10.0.0.20", device.getIpAddress());
    }

    // 8. A legacy row (NULL device_id) later registers with a deviceId: adopted, not duplicated.
    @Test
    void legacyEndpoint_laterRegistersWithDeviceId_adoptsDeviceId() {
        String mac = randomMacAddress();
        String deviceId = newDeviceId();

        AgentRegisterResponse legacy = register(request(null, mac, "10.0.0.10", "LEGACY-PC"));
        AgentRegisterResponse upgraded = register(request(deviceId, mac, "10.0.0.10", "LEGACY-PC"));

        assertEquals(legacy.endpointId(), upgraded.endpointId());
        assertEquals(deviceId, endpointDeviceRepository.findById(legacy.endpointId()).orElseThrow().getDeviceId());
        assertEquals(1, countByDeviceId(deviceId));
        assertEquals(legacy.endpointId(), endpointDeviceRepository.findByMacAddress(mac).orElseThrow().getId());
    }

    // 9. Heartbeat with the token issued by registration keeps updating the same row.
    @Test
    void heartbeatAfterRegistration_updatesSameDevice() throws Exception {
        String deviceId = newDeviceId();
        String mac = randomMacAddress();
        AgentRegisterResponse first = register(request(deviceId, mac, "10.0.0.10", "LAB-PC-01"));
        // Re-registration (e.g. lost state file) issues a fresh token for the SAME row.
        AgentRegisterResponse second = register(request(deviceId, randomMacAddress(), "10.0.0.10", "LAB-PC-01"));
        assertEquals(first.endpointId(), second.endpointId());

        mockMvc.perform(post("/agents/heartbeat")
                .servletPath("/agents/heartbeat")
                .header("X-Agent-Token", second.agentToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"cpuUsagePct": 1.5, "ramUsagePct": 2.5, "diskUsagePct": 3.5, "ipAddress": "10.9.9.9"}
                    """))
            .andExpect(status().isOk());

        EndpointDevice device = endpointDeviceRepository.findById(second.endpointId()).orElseThrow();
        assertEquals(EndpointDevice.Status.ONLINE, device.getStatus());
        assertNotNull(device.getLastHeartbeatAt());
        assertEquals("10.9.9.9", device.getIpAddress());
        assertEquals(1, countByDeviceId(deviceId));
    }

    // 10. The database itself prevents two rows from owning the same deviceId.
    @Test
    void duplicateDeviceId_isRejectedByDatabase() {
        String deviceId = newDeviceId();
        persistEndpoint(deviceId);

        assertThrows(DataIntegrityViolationException.class, () -> persistEndpoint(deviceId));
        assertEquals(1, countByDeviceId(deviceId));
    }

    // 10b. Legacy rows may all keep a NULL device_id (the unique index allows multiple NULLs).
    @Test
    void multipleLegacyRows_withNullDeviceId_areAllowed() {
        EndpointDevice first = persistEndpoint(null);
        EndpointDevice second = persistEndpoint(null);

        assertNotEquals(first.getId(), second.getId());
    }

    // Edge: same deviceId in a different case / with whitespace is the same device.
    @Test
    void deviceId_isNormalised_beforeLookup() {
        String deviceId = newDeviceId();
        String mac = randomMacAddress();

        AgentRegisterResponse first = register(request(deviceId, mac, "10.0.0.10", "LAB-PC-01"));
        AgentRegisterResponse second = register(request("  " + deviceId.toUpperCase() + "  ", mac, "10.0.0.10", "LAB-PC-01"));

        assertEquals(first.endpointId(), second.endpointId());
        assertEquals(1, countByDeviceId(deviceId));
    }

    // Edge: a blank deviceId is treated as absent (never stored as '', which would collide).
    @Test
    void blankDeviceId_isTreatedAsAbsent() {
        AgentRegisterResponse first = register(request("   ", randomMacAddress(), "10.0.0.10", "BLANK-1"));
        AgentRegisterResponse second = register(request("", randomMacAddress(), "10.0.0.11", "BLANK-2"));

        assertNotEquals(first.endpointId(), second.endpointId());
        assertNull(endpointDeviceRepository.findById(first.endpointId()).orElseThrow().getDeviceId());
        assertNull(endpointDeviceRepository.findById(second.endpointId()).orElseThrow().getDeviceId());
    }

    // Edge: a MAC already owned by a row with a DIFFERENT deviceId is not hijacked and no row is created.
    @Test
    void macOwnedByDifferentDeviceId_isRejected_withoutHijackingOrDuplicating() {
        String mac = randomMacAddress();
        String ownerDeviceId = newDeviceId();
        AgentRegisterResponse owner = register(request(ownerDeviceId, mac, "10.0.0.10", "OWNER-PC"));

        assertThrows(IllegalArgumentException.class,
            () -> agentService.register(request(newDeviceId(), mac, "10.0.0.11", "OTHER-PC"),
                agentProperties.registrationSecret()));

        EndpointDevice unchanged = endpointDeviceRepository.findById(owner.endpointId()).orElseThrow();
        assertEquals(ownerDeviceId, unchanged.getDeviceId());
        assertEquals("OWNER-PC", unchanged.getHostname());
        assertEquals(owner.endpointId(), endpointDeviceRepository.findByMacAddress(mac).orElseThrow().getId());
    }

    // Edge: a deviceId'd device reports a MAC owned by another row: registration succeeds, old MAC kept, no duplicate.
    @Test
    void changedMac_ownedByAnotherRow_keepsExistingMac() {
        String otherMac = randomMacAddress();
        AgentRegisterResponse other = register(request(null, otherMac, "10.0.0.50", "LEGACY-OTHER"));

        String deviceId = newDeviceId();
        String ownMac = randomMacAddress();
        AgentRegisterResponse own = register(request(deviceId, ownMac, "10.0.0.10", "LAB-PC-01"));
        AgentRegisterResponse again = register(request(deviceId, otherMac, "10.0.0.10", "LAB-PC-01"));

        assertEquals(own.endpointId(), again.endpointId());
        assertEquals(ownMac, endpointDeviceRepository.findById(own.endpointId()).orElseThrow().getMacAddress());
        assertEquals(other.endpointId(), endpointDeviceRepository.findByMacAddress(otherMac).orElseThrow().getId());
        assertEquals(1, countByDeviceId(deviceId));
    }

    @AfterEach
    void cleanUpCommittedFixtures() {
        createdEndpointIds.forEach(id -> {
            if (endpointDeviceRepository.existsById(id)) {
                endpointDeviceRepository.deleteById(id);
            }
        });
        createdEndpointIds.clear();
    }

    private AgentRegisterResponse register(AgentRegisterRequest request) {
        AgentRegisterResponse response = agentService.register(request, agentProperties.registrationSecret());
        createdEndpointIds.add(response.endpointId());
        return response;
    }

    private static AgentRegisterRequest request(String deviceId, String mac, String ip, String hostname) {
        return new AgentRegisterRequest(hostname, mac, ip, "Windows", "10.0.19045", "Test CPU",
            8192, 256, "0.1.0-TEST", null, deviceId);
    }

    private long countByDeviceId(String deviceId) {
        return endpointDeviceRepository.findAll().stream()
            .filter(e -> deviceId.equals(e.getDeviceId()))
            .count();
    }

    private EndpointDevice persistEndpoint(String deviceId) {
        EndpointDevice endpoint = new EndpointDevice();
        endpoint.setHostname("IT-AGENT-REGISTRATION-" + UUID.randomUUID());
        endpoint.setMacAddress(randomMacAddress());
        endpoint.setDeviceId(deviceId);
        endpoint.setAgentTokenHash(TokenHasher.sha256Hex("it-agent-registration-token-" + UUID.randomUUID()));
        EndpointDevice saved = endpointDeviceRepository.saveAndFlush(endpoint);
        createdEndpointIds.add(saved.getId());
        return saved;
    }

    private static String newDeviceId() {
        return UUID.randomUUID().toString().toLowerCase();
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
