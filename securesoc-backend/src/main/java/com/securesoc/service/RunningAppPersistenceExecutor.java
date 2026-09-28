package com.securesoc.service;

import com.securesoc.entity.RunningAppSnapshot;
import com.securesoc.repository.RunningAppSnapshotRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a single {@link RunningAppSnapshot} (and its cascaded
 * {@code RunningApp} rows) in its own independent ({@code REQUIRES_NEW})
 * transaction, deliberately separate from - and always committed before -
 * whatever detection/alert/risk processing runs afterwards.
 *
 * Mirrors {@link VpnEventPersistenceExecutor} exactly, for the same
 * reason: {@code SuspiciousProcessDetector}'s repository query
 * ({@code RunningAppRepository.existsBySnapshot_Endpoint_IdAndProcessNameIgnoreCaseAndSnapshot_CapturedAtAfter})
 * must be able to see the CURRENT snapshot's {@code RunningApp} rows,
 * which requires them to already be committed - not just flushed -
 * before {@link DetectionEvaluationExecutor}'s own {@code REQUIRES_NEW}
 * transaction begins. See {@link VpnEventPersistenceExecutor}'s Javadoc
 * for the full PostgreSQL READ COMMITTED visibility explanation, which
 * applies identically here.
 *
 * {@code MonitoringService.recordRunningApps()} must call this method
 * rather than {@code runningAppSnapshotRepository.save(...)} directly,
 * and must not itself be {@code @Transactional} wrapping both this call
 * and the detection call - doing so would put them back in one shared
 * transaction and reintroduce the same visibility problem.
 */
@Component
public class RunningAppPersistenceExecutor {

    private final RunningAppSnapshotRepository runningAppSnapshotRepository;

    public RunningAppPersistenceExecutor(RunningAppSnapshotRepository runningAppSnapshotRepository) {
        this.runningAppSnapshotRepository = runningAppSnapshotRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunningAppSnapshot persist(RunningAppSnapshot snapshot) {
        return runningAppSnapshotRepository.saveAndFlush(snapshot);
    }
}
