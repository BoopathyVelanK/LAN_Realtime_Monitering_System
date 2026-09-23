package com.securesoc.integration;

import com.securesoc.dto.AuthResponse;
import com.securesoc.dto.LoginRequest;
import com.securesoc.dto.RiskScoreResponse;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.FacultyAssignment;
import com.securesoc.entity.Laboratory;
import com.securesoc.entity.RiskScore;
import com.securesoc.entity.Role;
import com.securesoc.entity.User;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.repository.FacultyAssignmentRepository;
import com.securesoc.repository.LaboratoryRepository;
import com.securesoc.repository.RiskScoreRepository;
import com.securesoc.repository.RoleRepository;
import com.securesoc.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-HTTP integration coverage for {@code RiskScoreController} -
 * {@code GET /risk-scores} and {@code GET /risk-scores/{endpointId}} -
 * through real Spring Security, JWT auth, a Flyway-migrated Testcontainers
 * PostgreSQL instance, and the real controller -> service -> repository
 * stack (RiskScoreController -> RiskScoreService -> RiskScoreRepository).
 *
 * Reuses AlertLifecycleIntegrationTest's TestRestTemplate / JWT /
 * Testcontainers conventions (same container image, same {@code
 * /auth/login} flow, same "reuse the Flyway-seeded ADMIN/FACULTY roles
 * rather than inserting duplicates" pattern) and
 * RiskScorePersistenceIntegrationTest's endpoint-fixture helpers (random
 * MAC address / agent-token-hash generation) - no parallel setup
 * convention is invented here.
 *
 * This class seeds {@link RiskScore} rows directly via the repository
 * (mirroring how AlertLifecycleIntegrationTest seeds an {@code Alert}
 * directly via {@code AlertRepository}), rather than driving
 * {@code DetectionEngine} the way RiskScorePersistenceIntegrationTest
 * does - that class already covers the detection-to-risk-score pipeline;
 * this one only covers the REST/RBAC surface on top of an
 * already-persisted row, per the RiskScoreController milestone scope.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RiskScoreLifecycleIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private LaboratoryRepository laboratoryRepository;
    @Autowired
    private FacultyAssignmentRepository facultyAssignmentRepository;
    @Autowired
    private EndpointDeviceRepository endpointDeviceRepository;
    @Autowired
    private RiskScoreRepository riskScoreRepository;

    private static final SecureRandom RANDOM = new SecureRandom();

    private EndpointDevice inScopeEndpoint;
    private EndpointDevice outOfScopeEndpoint;
    private String adminToken;
    private String facultyToken;

    @BeforeEach
    void setup() {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        Role facultyRole = roleRepository.findByName("FACULTY").orElseThrow();

        User adminUser = new User();
        adminUser.setUsername("risk_admin_tester");
        adminUser.setEmail("risk_admin_tester@securesoc.local");
        adminUser.setPasswordHash(passwordEncoder.encode("password"));
        adminUser.setFullName("Risk Admin Tester");
        adminUser.getRoles().add(adminRole);
        userRepository.save(adminUser);

        User facultyUser = new User();
        facultyUser.setUsername("risk_faculty_tester");
        facultyUser.setEmail("risk_faculty_tester@securesoc.local");
        facultyUser.setPasswordHash(passwordEncoder.encode("password"));
        facultyUser.setFullName("Risk Faculty Tester");
        facultyUser.getRoles().add(facultyRole);
        userRepository.save(facultyUser);

        Laboratory assignedLab = new Laboratory();
        assignedLab.setName("Risk Test Lab - Assigned");
        assignedLab.setCode("RSK-A-" + UUID.randomUUID().toString().substring(0, 8));
        laboratoryRepository.save(assignedLab);

        Laboratory otherLab = new Laboratory();
        otherLab.setName("Risk Test Lab - Other");
        otherLab.setCode("RSK-B-" + UUID.randomUUID().toString().substring(0, 8));
        laboratoryRepository.save(otherLab);

        FacultyAssignment assignment = new FacultyAssignment();
        assignment.setFacultyUser(facultyUser);
        assignment.setLaboratory(assignedLab);
        facultyAssignmentRepository.save(assignment);

        inScopeEndpoint = persistEndpoint(assignedLab, "RISK-IT-IN-SCOPE");
        outOfScopeEndpoint = persistEndpoint(otherLab, "RISK-IT-OUT-OF-SCOPE");

        persistRiskScore(inScopeEndpoint, (short) 45, RiskScore.Level.HIGH);
        persistRiskScore(outOfScopeEndpoint, (short) 20, RiskScore.Level.LOW);

        adminToken = login("risk_admin_tester");
        facultyToken = login("risk_faculty_tester");
    }

    @AfterEach
    void cleanup() {
        riskScoreRepository.deleteAll();
        endpointDeviceRepository.deleteAll();
        facultyAssignmentRepository.deleteAll();
        laboratoryRepository.deleteAll();
        userRepository.deleteAll();
        // roleRepository is deliberately untouched: ADMIN/FACULTY are the
        // shared, Flyway-seeded rows (see setup()), not rows this test owns.
    }

    @Test
    void adminGetAllRiskScores_returnsSeededRow() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<List<RiskScoreResponse>> response = restTemplate.exchange(
                "/risk-scores",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<RiskScoreResponse> scores = response.getBody();
        assertNotNull(scores);
        assertTrue(scores.stream().anyMatch(r -> r.endpointId().equals(inScopeEndpoint.getId())
                && r.score() == 45 && "HIGH".equals(r.level())));
    }

    @Test
    void adminGetRiskScoreForEndpoint_returnsExpectedRow() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<RiskScoreResponse> response = restTemplate.exchange(
                "/risk-scores/" + inScopeEndpoint.getId(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                RiskScoreResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RiskScoreResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(inScopeEndpoint.getId(), body.endpointId());
        assertEquals((short) 45, body.score());
        assertEquals("HIGH", body.level());
    }

    @Test
    void getRiskScoreForNonexistentEndpoint_returns404() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<String> response = restTemplate.exchange(
                "/risk-scores/" + UUID.randomUUID(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void facultyAssignedToLab_canAccessRiskScoreForEndpointInScope() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(facultyToken);

        ResponseEntity<RiskScoreResponse> response = restTemplate.exchange(
                "/risk-scores/" + inScopeEndpoint.getId(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                RiskScoreResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RiskScoreResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(inScopeEndpoint.getId(), body.endpointId());
        assertEquals((short) 45, body.score());
    }

    @Test
    void facultyNotAssignedToLab_cannotAccessRiskScoreForEndpointOutOfScope() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(facultyToken);

        ResponseEntity<String> response = restTemplate.exchange(
                "/risk-scores/" + outOfScopeEndpoint.getId(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    private String login(String username) {
        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login",
                new LoginRequest(username, "password"),
                AuthResponse.class
        );
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        return loginResponse.getBody().accessToken();
    }

    private EndpointDevice persistEndpoint(Laboratory lab, String hostnamePrefix) {
        EndpointDevice endpoint = new EndpointDevice();
        endpoint.setHostname(hostnamePrefix + "-" + UUID.randomUUID());
        endpoint.setMacAddress(randomMacAddress());
        endpoint.setAgentTokenHash(randomHex(64));
        endpoint.setLab(lab);
        return endpointDeviceRepository.save(endpoint);
    }

    private RiskScore persistRiskScore(EndpointDevice endpoint, short score, RiskScore.Level level) {
        RiskScore riskScore = new RiskScore();
        riskScore.setEndpoint(endpoint);
        riskScore.setScore(score);
        riskScore.setLevel(level);
        return riskScoreRepository.save(riskScore);
    }

    /** endpoint_devices.mac_address is VARCHAR(17) UNIQUE NOT NULL, formatted
     * AA:BB:CC:DD:EE:FF - mirrors RiskScorePersistenceIntegrationTest's
     * helper of the same name/shape. */
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

    /** endpoint_devices.agent_token_hash is VARCHAR(64) UNIQUE NOT NULL -
     * mirrors RiskScorePersistenceIntegrationTest's helper of the same
     * name/shape. */
    private static String randomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        while (sb.length() < length) {
            sb.append(Integer.toHexString(RANDOM.nextInt(16)));
        }
        return sb.toString();
    }
}
