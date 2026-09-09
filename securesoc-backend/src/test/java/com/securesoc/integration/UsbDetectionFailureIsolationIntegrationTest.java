package com.securesoc.integration;

import com.securesoc.detection.DetectionContext;
import com.securesoc.detection.DetectionEngine;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.UsbEvent;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.repository.UsbEventRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, against a real PostgreSQL transaction (not a mocked
 * repository), that a failure in post-ingestion detection processing does
 * not roll back an already-ingested USB telemetry event. See
 * DetectionEvaluationExecutor's javadoc for the transaction-boundary
 * reasoning this test is verifying.
 *
 * DetectionEngine itself is mocked (rather than a real detector/rule
 * combination) so the failure can be forced deterministically; everything
 * downstream of that mock - DetectionEvaluationExecutor's REQUIRES_NEW
 * boundary, MonitoringService's try/catch, and the real UsbEvent
 * persistence/commit - is exercised for real through a live HTTP request
 * and a real Postgres container, exactly matching
 * UsbEventDetectionIntegrationTest's established conventions.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class UsbDetectionFailureIsolationIntegrationTest {

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
    private UsbEventRepository usbEventRepository;

    @Autowired
    private EndpointDeviceRepository endpointDeviceRepository;

    @MockBean
    private DetectionEngine detectionEngine;

    private static final String AGENT_TOKEN = "test-usb-isolation-token-123";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Set<UUID> createdEndpointIds = new LinkedHashSet<>();

    @Test
    void detectionEngineThrows_usbEventIsStillCommitted_andIngestStillReturnsOk() throws Exception {
        EndpointDevice endpoint = persistEndpoint();

        doThrow(new RuntimeException("simulated detection failure"))
            .when(detectionEngine).evaluate(ArgumentMatchers.any(DetectionContext.class));

        String payload = """
            {
              "deviceName": "SanDisk Ultra",
              "deviceId": "USB\\\\VID_0781&PID_5581",
              "vendorId": "0781",
              "productId": "5581",
              "action": "CONNECTED"
            }
            """;

        // If the outer @Transactional recordUsb() method were rolled back
        // by the mocked DetectionEngine's exception, this request would
        // surface as a 5xx - it must not.
        mockMvc.perform(post("/monitoring/usb")
                .servletPath("/monitoring/usb")
                .header("X-Agent-Token", AGENT_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        // The real, decisive assertion: querying a fresh read against the
        // real database (not the same persistence context that performed
        // the write) proves the UsbEvent row was actually committed, not
        // merely flushed-then-rolled-back.
        List<UsbEvent> events = usbEventRepository
            .findByEndpoint_IdOrderByEventTimeDesc(endpoint.getId(), Pageable.unpaged())
            .getContent();

        assertEquals(1, events.size(), "The USB telemetry event must survive a detection engine failure.");
        assertEquals("SanDisk Ultra", events.get(0).getDeviceName());
    }

    @AfterEach
    void cleanUpCommittedFixtures() {
        usbEventRepository.findAll().stream()
            .filter(event -> event.getEndpoint() != null && createdEndpointIds.contains(event.getEndpoint().getId()))
            .forEach(usbEventRepository::delete);

        createdEndpointIds.forEach(endpointDeviceRepository::deleteById);
        createdEndpointIds.clear();
    }

    private EndpointDevice persistEndpoint() {
        EndpointDevice endpoint = new EndpointDevice();
        endpoint.setHostname("IT-USB-ISOLATION-ENDPOINT-" + UUID.randomUUID());
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
