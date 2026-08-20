package com.securesoc.service;

import com.securesoc.entity.Alert;
import com.securesoc.repository.AlertRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolates the actual INSERT of a new (user-scoped, deduplicated) Alert
 * row into its own {@code REQUIRES_NEW} transaction, deliberately separate
 * from whatever transaction the caller is running in.
 *
 * Why this exists (see AlertService.createOrReuseForUser javadoc for the
 * full dedup flow): {@code DetectionEngine.evaluate()} is itself
 * {@code @Transactional} and calls {@code AlertService.createAlertFrom(...)}
 * for every detected result in a single loop - all inside ONE physical
 * transaction, since AlertService's own {@code @Transactional} method uses
 * the default REQUIRED propagation and simply joins whatever transaction
 * called it. Per the JPA spec, once a flush throws a PersistenceException
 * (which a unique-constraint violation becomes, after Spring's exception
 * translation, as a DataIntegrityViolationException), the EntityManager
 * for that transaction is unusable for the remainder of the transaction.
 *
 * If the partial-unique-index violation from V8__add_alert_open_dedup_index.sql
 * happened directly inside that shared transaction, catching it there and
 * trying to keep going (e.g. re-querying for the alert a concurrent
 * request just committed) would not be safe - it would either fail
 * outright or silently corrupt every other alert DetectionEngine is still
 * trying to persist within that same evaluate() call.
 *
 * Running the insert attempt in REQUIRES_NEW means only this small nested
 * transaction is rolled back on a constraint violation; the caller's
 * transaction (and its EntityManager) is completely unaffected and can
 * safely continue - which is exactly what AlertService does by re-querying
 * for the alert the concurrent winner committed.
 */
@Component
public class AlertInsertExecutor {

    private final AlertRepository alertRepository;

    public AlertInsertExecutor(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /**
     * Persists {@code alert} in a brand-new transaction and flushes
     * immediately, so that a partial-unique-index violation surfaces here
     * - as a {@code DataIntegrityViolationException} - rather than staying
     * buffered until some later, unrelated flush point in the caller's
     * transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Alert insertAlert(Alert alert) {
        return alertRepository.saveAndFlush(alert);
    }
}
