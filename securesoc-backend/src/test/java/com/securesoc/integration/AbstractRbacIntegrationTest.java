package com.securesoc.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securesoc.dto.AuthResponse;
import com.securesoc.dto.LoginRequest;
import com.securesoc.entity.Alert;
import com.securesoc.entity.Department;
import com.securesoc.entity.DetectionRule;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.FacultyAssignment;
import com.securesoc.entity.IdleEvent;
import com.securesoc.entity.InternetUsageEvent;
import com.securesoc.entity.Laboratory;
import com.securesoc.entity.LoginEvent;
import com.securesoc.entity.LogoutEvent;
import com.securesoc.entity.NetworkUsageEvent;
import com.securesoc.entity.RiskScore;
import com.securesoc.entity.Role;
import com.securesoc.entity.RunningApp;
import com.securesoc.entity.RunningAppSnapshot;
import com.securesoc.entity.UsbEvent;
import com.securesoc.entity.User;
import com.securesoc.entity.VpnEvent;
import com.securesoc.repository.AlertRepository;
import com.securesoc.repository.DepartmentRepository;
import com.securesoc.repository.DetectionRuleRepository;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.repository.FacultyAssignmentRepository;
import com.securesoc.repository.IdleEventRepository;
import com.securesoc.repository.InternetUsageEventRepository;
import com.securesoc.repository.LaboratoryRepository;
import com.securesoc.repository.LoginEventRepository;
import com.securesoc.repository.LogoutEventRepository;
import com.securesoc.repository.NetworkUsageEventRepository;
import com.securesoc.repository.RiskScoreRepository;
import com.securesoc.repository.RoleRepository;
import com.securesoc.repository.RunningAppSnapshotRepository;
import com.securesoc.repository.UsbEventRepository;
import com.securesoc.repository.UserRepository;
import com.securesoc.repository.VpnEventRepository;
import com.securesoc.security.TokenHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * F7 (RBAC end-to-end verification) shared fixture. Subclasses exercise the
 * real application - real Spring Security filter chain, real JWT login,
 * real Flyway-migrated PostgreSQL - never mocks.
 *
 * <pre>
 *   Department A -> Lab A -> Endpoint A (agent token known)
 *   Department B -> Lab B -> Endpoint B
 *   rbac_admin       ADMIN
 *   rbac_faculty_f   FACULTY, assigned to Lab A ONLY
 *   rbac_faculty_z   FACULTY, ZERO assignments
 * </pre>
 *
 * Every monitoring table has one row per endpoint; there is one OPEN alert
 * per endpoint, one OPEN alert with no endpoint, and one risk score per
 * endpoint.
 *
 * Container lifecycle: a SINGLETON container started in a static
 * initializer (not {@code @Container}). Spring's context cache key does not
 * include the DynamicPropertySource's resolved values, so with a per-class
 * {@code @Container} the second subclass would reuse a cached context still
 * pointing at the first class's stopped container. One shared container
 * keeps every subclass on the same context and the same database
 * (Testcontainers' Ryuk reaps it at JVM exit).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractRbacIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    protected static final String PASSWORD = "password";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired protected TestRestTemplate restTemplate;
    @Autowired protected ObjectMapper objectMapper;
    @LocalServerPort protected int port;

    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected UserRepository userRepository;
    @Autowired protected RoleRepository roleRepository;
    @Autowired protected DepartmentRepository departmentRepository;
    @Autowired protected LaboratoryRepository laboratoryRepository;
    @Autowired protected FacultyAssignmentRepository facultyAssignmentRepository;
    @Autowired protected EndpointDeviceRepository endpointDeviceRepository;
    @Autowired protected DetectionRuleRepository detectionRuleRepository;
    @Autowired protected AlertRepository alertRepository;
    @Autowired protected RiskScoreRepository riskScoreRepository;
    @Autowired protected LoginEventRepository loginEventRepository;
    @Autowired protected LogoutEventRepository logoutEventRepository;
    @Autowired protected UsbEventRepository usbEventRepository;
    @Autowired protected VpnEventRepository vpnEventRepository;
    @Autowired protected IdleEventRepository idleEventRepository;
    @Autowired protected NetworkUsageEventRepository networkUsageEventRepository;
    @Autowired protected InternetUsageEventRepository internetUsageEventRepository;
    @Autowired protected RunningAppSnapshotRepository runningAppSnapshotRepository;

    protected Department deptA;
    protected Department deptB;
    protected Laboratory labA;
    protected Laboratory labB;
    protected User adminUser;
    protected User facultyF;
    protected User facultyZ;
    protected EndpointDevice endpointA;
    protected EndpointDevice endpointB;
    protected String agentTokenA;
    protected DetectionRule rule;
    protected Alert alertA;
    protected Alert alertB;
    protected Alert alertNoEndpoint;

    protected String adminToken;
    protected String facultyFToken;
    protected String facultyZToken;

    // ===================================================================
    // Fixture
    // ===================================================================

    @BeforeEach
    void seedRbacFixture() {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        Role facultyRole = roleRepository.findByName("FACULTY").orElseThrow();
        String passwordHash = passwordEncoder.encode(PASSWORD);

        adminUser = persistUser("rbac_admin", passwordHash, adminRole);
        facultyF = persistUser("rbac_faculty_f", passwordHash, facultyRole);
        facultyZ = persistUser("rbac_faculty_z", passwordHash, facultyRole);

        deptA = persistDepartment("RBAC Dept A", "RBAC-DA-");
        deptB = persistDepartment("RBAC Dept B", "RBAC-DB-");
        labA = persistLab("RBAC Lab A", "RBAC-A-", deptA);
        labB = persistLab("RBAC Lab B", "RBAC-B-", deptB);

        // Faculty F -> Lab A ONLY. Faculty Z deliberately gets no assignment.
        FacultyAssignment assignment = new FacultyAssignment();
        assignment.setFacultyUser(facultyF);
        assignment.setLaboratory(labA);
        facultyAssignmentRepository.save(assignment);

        agentTokenA = TokenHasher.generateOpaqueToken();
        endpointA = persistEndpoint(labA, "RBAC-EP-A", TokenHasher.sha256Hex(agentTokenA));
        endpointB = persistEndpoint(labB, "RBAC-EP-B", randomHex(64));

        rule = new DetectionRule();
        rule.setName("RBAC Test Rule " + UUID.randomUUID());
        rule.setDescription("Rule for RBAC E2E tests");
        rule.setEventSource("AUTH_FAILURE");
        rule.setSeverity(DetectionRule.Severity.HIGH);
        rule.setEnabled(true);
        rule = detectionRuleRepository.save(rule);

        alertA = persistAlert(endpointA, "Alert A");
        alertB = persistAlert(endpointB, "Alert B");
        alertNoEndpoint = persistAlert(null, "Alert with no endpoint");

        persistRiskScore(endpointA, (short) 40, RiskScore.Level.HIGH);
        persistRiskScore(endpointB, (short) 20, RiskScore.Level.LOW);

        seedMonitoringData(endpointA);
        seedMonitoringData(endpointB);

        adminToken = login("rbac_admin");
        facultyFToken = login("rbac_faculty_f");
        facultyZToken = login("rbac_faculty_z");
    }

    @AfterEach
    void cleanupRbacFixture() {
        // FK order: children first. Only rows this fixture can have created
        // are removed; the Flyway-seeded ADMIN/FACULTY roles are shared and
        // deliberately untouched.
        runningAppSnapshotRepository.deleteAll(); // cascades to running_apps
        loginEventRepository.deleteAll();
        logoutEventRepository.deleteAll();
        usbEventRepository.deleteAll();
        vpnEventRepository.deleteAll();
        idleEventRepository.deleteAll();
        networkUsageEventRepository.deleteAll();
        internetUsageEventRepository.deleteAll();
        alertRepository.deleteAll();
        riskScoreRepository.deleteAll();
        endpointDeviceRepository.deleteAll();
        facultyAssignmentRepository.deleteAll();
        laboratoryRepository.deleteAll();
        departmentRepository.deleteAll();
        detectionRuleRepository.delete(rule);
        userRepository.deleteAll(List.of(adminUser, facultyF, facultyZ));
    }

    // ===================================================================
    // HTTP helpers - always return the raw body so tests can assert on
    // actual returned records, not just the status code.
    // ===================================================================

    protected ResponseEntity<String> get(String bearerToken, String path) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    protected ResponseEntity<String> getWithAgentToken(String agentToken, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Agent-Token", agentToken);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    protected ResponseEntity<String> post(String bearerToken, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    protected ResponseEntity<String> postWithAgentToken(String agentToken, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Agent-Token", agentToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    protected JsonNode json(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new AssertionError("Response body is not valid JSON: " + response.getBody(), e);
        }
    }

    /** Collects {@code field} from every element of a JSON array (null values become "null"). */
    protected static Set<String> values(JsonNode array, String field) {
        Set<String> out = new HashSet<>();
        for (JsonNode element : array) {
            out.add(element.get(field).asText());
        }
        return out;
    }

    /** Collects {@code field} from every element of a PageResponse's {@code content} array. */
    protected Set<String> pageValues(ResponseEntity<String> response, String field) {
        return values(json(response).get("content"), field);
    }

    protected long monitoringRowCount() {
        return loginEventRepository.count() + logoutEventRepository.count() + usbEventRepository.count()
                + vpnEventRepository.count() + idleEventRepository.count() + networkUsageEventRepository.count()
                + internetUsageEventRepository.count() + runningAppSnapshotRepository.count();
    }

    protected String login(String username) {
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(username, PASSWORD), AuthResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody().accessToken();
    }

    // ===================================================================
    // Persistence helpers
    // ===================================================================

    private User persistUser(String username, String passwordHash, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@securesoc.local");
        user.setPasswordHash(passwordHash);
        user.setFullName(username);
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    private Department persistDepartment(String name, String codePrefix) {
        Department department = new Department();
        department.setName(name);
        department.setCode(codePrefix + shortId());
        return departmentRepository.save(department);
    }

    private Laboratory persistLab(String name, String codePrefix, Department department) {
        Laboratory lab = new Laboratory();
        lab.setName(name);
        lab.setCode(codePrefix + shortId());
        lab.setDepartment(department);
        return laboratoryRepository.save(lab);
    }

    private EndpointDevice persistEndpoint(Laboratory lab, String hostnamePrefix, String agentTokenHash) {
        EndpointDevice endpoint = new EndpointDevice();
        endpoint.setHostname(hostnamePrefix + "-" + UUID.randomUUID());
        endpoint.setMacAddress(randomMacAddress());
        endpoint.setAgentTokenHash(agentTokenHash);
        endpoint.setLab(lab);
        return endpointDeviceRepository.save(endpoint);
    }

    private Alert persistAlert(EndpointDevice endpoint, String title) {
        Alert alert = new Alert();
        alert.setRule(rule);
        alert.setEndpoint(endpoint);
        alert.setTitle(title);
        alert.setSeverity(Alert.Severity.HIGH);
        return alertRepository.save(alert);
    }

    private void persistRiskScore(EndpointDevice endpoint, short score, RiskScore.Level level) {
        RiskScore riskScore = new RiskScore();
        riskScore.setEndpoint(endpoint);
        riskScore.setScore(score);
        riskScore.setLevel(level);
        riskScoreRepository.save(riskScore);
    }

    private void seedMonitoringData(EndpointDevice endpoint) {
        java.time.Instant now = java.time.Instant.now();

        LoginEvent login = new LoginEvent();
        login.setEndpoint(endpoint);
        login.setOsUsername("student");
        login.setLoginTime(now);
        loginEventRepository.save(login);

        LogoutEvent logout = new LogoutEvent();
        logout.setEndpoint(endpoint);
        logout.setOsUsername("student");
        logout.setLogoutTime(now);
        logoutEventRepository.save(logout);

        UsbEvent usb = new UsbEvent();
        usb.setEndpoint(endpoint);
        usb.setDeviceName("Flash Drive");
        usb.setAction(UsbEvent.Action.CONNECTED);
        usbEventRepository.save(usb);

        VpnEvent vpn = new VpnEvent();
        vpn.setEndpoint(endpoint);
        vpn.setAdapterName("tun0");
        vpn.setActive(true);
        vpnEventRepository.save(vpn);

        IdleEvent idle = new IdleEvent();
        idle.setEndpoint(endpoint);
        idle.setIdleSeconds(30);
        idleEventRepository.save(idle);

        NetworkUsageEvent network = new NetworkUsageEvent();
        network.setEndpoint(endpoint);
        network.setBytesSent(100L);
        network.setBytesReceived(200L);
        networkUsageEventRepository.save(network);

        InternetUsageEvent internet = new InternetUsageEvent();
        internet.setEndpoint(endpoint);
        internet.setUploadMb(new java.math.BigDecimal("1.500"));
        internet.setDownloadMb(new java.math.BigDecimal("2.500"));
        internet.setPeriodSeconds(60);
        internetUsageEventRepository.save(internet);

        RunningAppSnapshot snapshot = new RunningAppSnapshot();
        snapshot.setEndpoint(endpoint);
        RunningApp app = new RunningApp();
        app.setSnapshot(snapshot);
        app.setProcessName("notepad.exe");
        app.setPid(1234);
        snapshot.getApps().add(app);
        runningAppSnapshotRepository.save(snapshot);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** endpoint_devices.mac_address is VARCHAR(17) UNIQUE NOT NULL (AA:BB:CC:DD:EE:FF). */
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

    /** endpoint_devices.agent_token_hash is VARCHAR(64) UNIQUE NOT NULL. */
    private static String randomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        while (sb.length() < length) {
            sb.append(Integer.toHexString(RANDOM.nextInt(16)));
        }
        return sb.toString();
    }
}
