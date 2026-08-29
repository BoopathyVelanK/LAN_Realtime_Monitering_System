package com.securesoc.service;

import com.securesoc.detection.DetectionContext;
import com.securesoc.entity.AuthFailureEvent;
import com.securesoc.entity.User;
import com.securesoc.repository.AuthFailureEventRepository;
import com.securesoc.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists the failed-login-attempt counter/lockout and the corresponding
 * {@link AuthFailureEvent} row in a dedicated {@code REQUIRES_NEW}
 * transaction, deliberately separate from whatever transaction the caller
 * ({@link AuthService#login}) is running in.
 *
 * Why this exists (Checkpoint E): {@code AuthService.login()} is itself
 * {@code @Transactional} (default {@code REQUIRED} propagation) and, on a
 * wrong-password attempt, throws {@code UnauthorizedException} immediately
 * after recording the failure. {@code UnauthorizedException} is an unchecked
 * exception with no {@code noRollbackFor} override, so Spring's default
 * rollback rule rolls back {@code login()}'s entire transaction the moment
 * that exception propagates out of it. Before this class existed, that meant
 * the failed-attempt counter, the account lockout, and the persisted
 * {@code AuthFailureEvent} row were all silently discarded on every failed
 * login - the very state this detection wiring (and the pre-existing
 * lockout feature) depends on.
 *
 * Running this class's persistence step in its own {@code REQUIRES_NEW}
 * transaction means it commits independently the moment {@link #recordFailure}
 * returns - before {@code login()} ever reaches its {@code throw} - so it is
 * completely unaffected by whatever {@code login()}'s own transaction does
 * afterward. This mirrors the isolation rationale already established by
 * {@link AlertInsertExecutor} elsewhere in this codebase, applied to the
 * opposite direction of the same underlying problem: there, a nested
 * transaction's failure must not poison the caller's transaction; here, the
 * caller's own eventual rollback must not poison this nested transaction's
 * already-intended commit.
 *
 * This class deliberately does NOT call {@code DetectionEngine.evaluate()}
 * itself. {@code DetectionEngine.evaluate()} is {@code @Transactional} with
 * default (REQUIRED) propagation, so calling it from within this method
 * would make it join - not replace - this same {@code REQUIRES_NEW}
 * transaction. Any exception surfacing from evaluate() would then mark this
 * shared transaction rollback-only, undoing the very counter/lockout/event
 * write this class exists to preserve. {@link AuthService#login} instead
 * calls {@code DetectionEngine.evaluate()} itself, using the
 * {@link DetectionContext} this method returns, only after this method's
 * transaction has already committed - see that method's inline comments for
 * the full call-ordering rationale.
 */
@Component
public class AuthFailureRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuthFailureRecorder.class);

    // Unchanged from the pre-Checkpoint-E AuthService.registerFailedAttempt -
    // locks an account for 5 minutes after 5 consecutive failed attempts.
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 5;

    private final UserRepository userRepository;
    private final AuthFailureEventRepository authFailureEventRepository;

    public AuthFailureRecorder(
        UserRepository userRepository,
        AuthFailureEventRepository authFailureEventRepository
    ) {
        this.userRepository = userRepository;
        this.authFailureEventRepository = authFailureEventRepository;
    }

    /**
     * Increments {@code userId}'s failed-login counter (locking the account
     * if the threshold is reached), persists a new {@link AuthFailureEvent}
     * row, and returns a {@link DetectionContext} describing that event for
     * the caller to hand to {@code DetectionEngine.evaluate()} afterward.
     *
     * {@code userId} is looked up fresh via {@link UserRepository} rather
     * than accepting a {@code User} entity from the caller's own
     * transaction - matching the entity-resolution convention already
     * established in {@code AlertService}/{@code RiskScoreService} - since
     * this method's own persistence context is otherwise unrelated to
     * whatever transaction/session the caller's {@code User} instance came
     * from.
     *
     * Returns {@code null} - performing no writes - if {@code userId} does
     * not resolve to an existing user. This should not happen in practice
     * (the caller has just loaded this same user in its own transaction
     * moments earlier), but is handled defensively rather than throwing,
     * since a user vanishing between that load and this call is an
     * extremely narrow race, not a defect in the caller's logic, and
     * failing loudly here would surface as an unrelated 500 in place of the
     * intended "invalid credentials" response.
     *
     * The event's {@code eventSource} is always {@code "AUTH_FAILURE"} and
     * {@code endpointId} is always {@code null} - a portal login has no
     * schema-level relationship to any {@code EndpointDevice} (those are
     * agent-authenticated telemetry sources, entirely separate from
     * browser-authenticated {@code User} logins) - see
     * {@code RiskScoreService}'s own javadoc on the null-endpointId no-op
     * case, which applies unchanged here.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DetectionContext recordFailure(UUID userId, String sourceIp) {
        Optional<User> maybeUser = userRepository.findById(userId);
        if (maybeUser.isEmpty()) {
            log.warn("AuthFailureRecorder.recordFailure called for unknown user id {} - skipping.", userId);
            return null;
        }
        User user = maybeUser.get();

        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plusSeconds(LOCKOUT_MINUTES * 60));
        }
        userRepository.save(user);

        AuthFailureEvent event = new AuthFailureEvent();
        event.setUser(user);
        event.setSourceIp(sourceIp);
        authFailureEventRepository.save(event);

        return new DetectionContext("AUTH_FAILURE", null, user.getId(), event.getAttemptedAt(), event);
    }
}
