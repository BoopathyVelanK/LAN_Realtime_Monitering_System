package com.securesoc.service;

import com.securesoc.entity.NetworkUsageEvent;
import com.securesoc.repository.NetworkUsageEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a NetworkUsageEvent in its own REQUIRES_NEW transaction and
 * flushes it, so the row is committed before MonitoringService starts
 * detection.
 *
 * NetworkUsageDetector sums bytes with a database query that runs inside
 * DetectionEvaluationExecutor's own REQUIRES_NEW transaction. Under
 * PostgreSQL READ COMMITTED, that transaction cannot see an uncommitted row
 * from a suspended outer transaction, so without committing first the sum
 * would silently miss the very sample being evaluated. Same reasoning and
 * same shape as UsbEventPersistenceExecutor / VpnEventPersistenceExecutor.
 */
@Component
public class NetworkUsageEventPersistenceExecutor {

    private final NetworkUsageEventRepository networkUsageEventRepository;

    public NetworkUsageEventPersistenceExecutor(NetworkUsageEventRepository networkUsageEventRepository) {
        this.networkUsageEventRepository = networkUsageEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NetworkUsageEvent persist(NetworkUsageEvent event) {
        return networkUsageEventRepository.saveAndFlush(event);
    }
}
