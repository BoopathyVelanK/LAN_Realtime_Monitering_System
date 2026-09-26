package com.securesoc.service;

import com.securesoc.entity.VpnEvent;
import com.securesoc.repository.VpnEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a single {@link VpnEvent} in its own independent
 * ({@code REQUIRES_NEW}) transaction, deliberately separate from - and
 * always committed before - whatever detection/alert/risk processing
 * runs afterwards.
 *
 * Why this exists: {@code MonitoringService.recordVpn()} must guarantee
 * two things that turned out to be in tension when USB persistence and
 * detection shared one transaction (see {@link UsbEventPersistenceExecutor},
 * whose pattern this class mirrors exactly for VPN events):
 *   1. A detection failure must never roll back the VPN telemetry event
 *      (see {@link DetectionEvaluationExecutor} - detection runs in its
 *      own REQUIRES_NEW transaction precisely so a failure there can't
 *      poison the caller's transaction or EntityManager).
 *   2. Detection - specifically {@code VpnEventDetector}'s threshold
 *      query, {@code VpnEventRepository.countByEndpoint_IdAndActiveTrueAndDetectedAtAfter},
 *      a plain database COUNT - must be able to see the CURRENT VPN event
 *      it is meant to evaluate, not just previously-committed ones.
 *
 * These two requirements are incompatible if VPN persistence happens
 * inside {@code recordVpn()}'s own transaction while detection runs in a
 * separate REQUIRES_NEW transaction: under PostgreSQL's READ COMMITTED
 * isolation, a REQUIRES_NEW transaction cannot see rows still uncommitted
 * in the suspended outer transaction. A detector counting rows in the
 * database would then silently miss the very row that was supposed to
 * trigger it - this is exactly the failure mode
 * {@link UsbEventPersistenceExecutor} was introduced to avoid for USB.
 *
 * The fix is sequencing, not nesting: this class commits the VPN event on
 * its own, in its own transaction, before {@code recordVpn()} ever calls
 * {@link DetectionEvaluationExecutor}. By the time that separate
 * REQUIRES_NEW transaction starts, this one has already committed, and
 * the row is visible under READ COMMITTED like any other previously
 * committed row - while remaining fully protected from any detection
 * failure, since persistence already succeeded and committed first.
 *
 * {@code recordVpn()} must call this method rather than
 * {@code vpnEventRepository.saveAndFlush(...)} directly, and must not
 * itself be {@code @Transactional} wrapping both this call and the
 * detection call - doing so would put them back in one shared
 * transaction and reintroduce the same visibility problem.
 */
@Component
public class VpnEventPersistenceExecutor {

    private final VpnEventRepository vpnEventRepository;

    public VpnEventPersistenceExecutor(VpnEventRepository vpnEventRepository) {
        this.vpnEventRepository = vpnEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VpnEvent persist(VpnEvent event) {
        return vpnEventRepository.saveAndFlush(event);
    }
}
