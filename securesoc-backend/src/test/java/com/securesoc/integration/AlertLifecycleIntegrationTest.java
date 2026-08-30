package com.securesoc.integration;

import com.securesoc.dto.AlertResponse;
import com.securesoc.dto.AuthResponse;
import com.securesoc.dto.LoginRequest;
import com.securesoc.entity.Alert;
import com.securesoc.entity.DetectionRule;
import com.securesoc.entity.Role;
import com.securesoc.entity.User;
import com.securesoc.repository.AlertRepository;
import com.securesoc.repository.DetectionRuleRepository;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AlertLifecycleIntegrationTest {

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
    private DetectionRuleRepository ruleRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private com.securesoc.repository.RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private String jwtToken;
    private Alert testAlert;

    @BeforeEach
    void setup() {
        testUser = new User();
        testUser.setUsername("alert_tester");
        testUser.setEmail("tester@securesoc.local");
        testUser.setPasswordHash(passwordEncoder.encode("password"));
        testUser.setFullName("Alert Tester");

        Role analystRole = new Role("ROLE_ANALYST");
        roleRepository.save(analystRole);
        testUser.getRoles().add(analystRole);

        testUser = userRepository.save(testUser);

        DetectionRule rule = new DetectionRule();
        rule.setName("Test Rule");
        rule.setDescription("Rule for testing");
        rule.setEventSource("AUTH_FAILURE");
        rule.setSeverity(DetectionRule.Severity.HIGH);
        rule.setEnabled(true);
        rule = ruleRepository.save(rule);

        testAlert = new Alert();
        testAlert.setRule(rule);
        testAlert.setTitle("Test Alert");
        testAlert.setSeverity(Alert.Severity.HIGH);
        testAlert = alertRepository.save(testAlert);

        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login",
                new LoginRequest("alert_tester", "password"),
                AuthResponse.class
        );
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        jwtToken = loginResponse.getBody().accessToken();
    }

    @AfterEach
    void cleanup() {
        alertRepository.deleteAll();
        ruleRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
    }

    @Test
    void testGetAlerts() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtToken);

        ResponseEntity<List<AlertResponse>> response = restTemplate.exchange(
                "/alerts",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<AlertResponse> alerts = response.getBody();
        assertNotNull(alerts);
        assertFalse(alerts.isEmpty());
        assertEquals("Test Alert", alerts.get(0).title());
    }

    @Test
    void testAcknowledgeAndResolveLifecycle() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtToken);

        // Acknowledge
        ResponseEntity<AlertResponse> ackResponse = restTemplate.exchange(
                "/alerts/" + testAlert.getId() + "/acknowledge",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                AlertResponse.class
        );

        assertEquals(HttpStatus.OK, ackResponse.getStatusCode());
        AlertResponse ackBody = ackResponse.getBody();
        assertNotNull(ackBody);
        assertEquals("ACKNOWLEDGED", ackBody.status());
        assertEquals(testUser.getId(), ackBody.acknowledgedByUserId());
        assertNotNull(ackBody.acknowledgedAt());

        // Verify DB update
        Alert dbAlert = alertRepository.findById(testAlert.getId()).orElseThrow();
        assertEquals(Alert.Status.ACKNOWLEDGED, dbAlert.getStatus());
        assertEquals(testUser.getId(), dbAlert.getAcknowledgedBy().getId());

        // Resolve
        ResponseEntity<AlertResponse> resResponse = restTemplate.exchange(
                "/alerts/" + testAlert.getId() + "/resolve",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                AlertResponse.class
        );

        assertEquals(HttpStatus.OK, resResponse.getStatusCode());
        AlertResponse resBody = resResponse.getBody();
        assertNotNull(resBody);
        assertEquals("RESOLVED", resBody.status());
        assertNotNull(resBody.resolvedAt());

        // Verify DB update
        dbAlert = alertRepository.findById(testAlert.getId()).orElseThrow();
        assertEquals(Alert.Status.RESOLVED, dbAlert.getStatus());
        assertNotNull(dbAlert.getResolvedAt());
    }
}
