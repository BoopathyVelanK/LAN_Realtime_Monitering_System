package com.securesoc.integration;

import com.securesoc.entity.EndpointDevice;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.security.TokenHasher;
import com.securesoc.service.EndpointOfflineSweeper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Real-PostgreSQL proof that EndpointOfflineSweeper.sweep() flips only
 * ONLINE endpoints whose heartbeat is older than the configured timeout
 * (60s by default). sweep() is invoked directly: the test suite runs with
 * scheduling disabled, so no background sweeper can race these assertions
 * (and the first test proves that isolation is really active).
 */
@SpringBootTest
class EndpointOfflineSweeperIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = SharedPostgresContainer.INSTANCE;

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private EndpointOfflineSweeper sweeper;

    @Autowired
    private EndpointDeviceRepository endpointDeviceRepository;

    @Autowired
    private ApplicationContext applicationContext;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Set<UUID> createdEndpointIds = new LinkedHashSet<>();

    @Test
    void backgroundScheduling_isDisabledInTestContexts() {
        assertFalse(applicationContext.containsBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME),
            "securesoc.scheduling.enabled=false must keep @Scheduled jobs out of test contexts");
    }

    @Test
    void sweep_marksOnlyStaleOnlineEndpointsOffline() {
        EndpointDevice stale = persistEndpoint(EndpointDevice.Status.ONLINE, Instant.now().minusSeconds(600));
        EndpointDevice fresh = persistEndpoint(EndpointDevice.Status.ONLINE, Instant.now().minusSeconds(2));
        EndpointDevice alreadyOffline = persistEndpoint(EndpointDevice.Status.OFFLINE, Instant.now().minusSeconds(600));

        sweeper.sweep();

        assertEquals(EndpointDevice.Status.OFFLINE, reload(stale).getStatus(),
            "An ONLINE endpoint past the heartbeat timeout must go OFFLINE");
        assertEquals(EndpointDevice.Status.ONLINE, reload(fresh).getStatus(),
            "An ONLINE endpoint with a recent heartbeat must stay ONLINE");
        assertEquals(EndpointDevice.Status.OFFLINE, reload(alreadyOffline).getStatus());
    }

    @Test
    void sweep_isIdempotent() {
        EndpointDevice stale = persistEndpoint(EndpointDevice.Status.ONLINE, Instant.now().minusSeconds(600));

        sweeper.sweep();
        sweeper.sweep();

        assertEquals(EndpointDevice.Status.OFFLINE, reload(stale).getStatus());
    }

    @AfterEach
    void cleanUpCommittedFixtures() {
        createdEndpointIds.forEach(endpointDeviceRepository::deleteById);
        createdEndpointIds.clear();
    }

    private EndpointDevice reload(EndpointDevice device) {
        return endpointDeviceRepository.findById(device.getId()).orElseThrow();
    }

    private EndpointDevice persistEndpoint(EndpointDevice.Status status, Instant lastHeartbeatAt) {
        EndpointDevice endpoint = new EndpointDevice();
        endpoint.setHostname("IT-SWEEPER-ENDPOINT-" + UUID.randomUUID());
        endpoint.setMacAddress(randomMacAddress());
        endpoint.setAgentTokenHash(TokenHasher.sha256Hex("it-sweeper-token-" + UUID.randomUUID()));
        endpoint.setStatus(status);
        endpoint.setLastHeartbeatAt(lastHeartbeatAt);
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
