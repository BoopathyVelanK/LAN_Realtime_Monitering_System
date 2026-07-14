package com.securesoc.service;

import com.securesoc.config.AgentProperties;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.repository.EndpointDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Runs every 15s and flips any endpoint whose last heartbeat is older than
 * securesoc.agent.heartbeat-timeout-seconds back to OFFLINE. This is what
 * makes "stop the agent and wait ~60s, then see it go OFFLINE" (documented
 * in securesoc-agent/README.md) actually true - without this job a device
 * would show ONLINE forever after its agent process dies.
 */
@Component
public class EndpointOfflineSweeper {

    private static final Logger log = LoggerFactory.getLogger(EndpointOfflineSweeper.class);

    private final EndpointDeviceRepository endpointDeviceRepository;
    private final AgentProperties agentProperties;

    public EndpointOfflineSweeper(EndpointDeviceRepository endpointDeviceRepository, AgentProperties agentProperties) {
        this.endpointDeviceRepository = endpointDeviceRepository;
        this.agentProperties = agentProperties;
    }

    @Scheduled(fixedRate = 15_000)
    @Transactional
    public void sweep() {
        Instant cutoff = Instant.now().minusSeconds(agentProperties.heartbeatTimeoutSeconds());
        List<EndpointDevice> stale = endpointDeviceRepository
            .findByStatusAndLastHeartbeatAtBefore(EndpointDevice.Status.ONLINE, cutoff);

        if (stale.isEmpty()) {
            return;
        }

        stale.forEach(device -> device.setStatus(EndpointDevice.Status.OFFLINE));
        endpointDeviceRepository.saveAll(stale);
        log.info("Marked {} endpoint(s) OFFLINE after missing heartbeat timeout ({}s).",
            stale.size(), agentProperties.heartbeatTimeoutSeconds());
    }
}
