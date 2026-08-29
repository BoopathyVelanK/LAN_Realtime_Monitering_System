package com.securesoc.service;

import com.securesoc.detection.DetectionContext;
import com.securesoc.detection.DetectionEngine;
import com.securesoc.dto.AuthResponse;
import com.securesoc.entity.RefreshToken;
import com.securesoc.entity.User;
import com.securesoc.exception.AccountLockedException;
import com.securesoc.exception.UnauthorizedException;
import com.securesoc.repository.RefreshTokenRepository;
import com.securesoc.repository.UserRepository;
import com.securesoc.security.JwtService;
import com.securesoc.security.TokenHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // Failed-attempt counting, lockout, and AuthFailureEvent persistence now
    // live in AuthFailureRecorder (Checkpoint E) - see that class for the
    // MAX_FAILED_ATTEMPTS/LOCKOUT_MINUTES constants and the REQUIRES_NEW
    // rationale (this method's own transaction is about to be rolled back
    // by the UnauthorizedException thrown right after, and that write must
    // survive it).

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthFailureRecorder authFailureRecorder;
    private final DetectionEngine detectionEngine;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        AuthFailureRecorder authFailureRecorder,
        DetectionEngine detectionEngine,
        PasswordEncoder passwordEncoder,
        JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authFailureRecorder = authFailureRecorder;
        this.detectionEngine = detectionEngine;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** Overload kept for any other caller/test that doesn't have a source
     * IP available - behaves exactly as before, just records the failure
     * event with a null source_ip. */
    @Transactional
    public AuthResponse login(String usernameOrEmail, String rawPassword) {
        return login(usernameOrEmail, rawPassword, null);
    }

    @Transactional
    public AuthResponse login(String usernameOrEmail, String rawPassword, String sourceIp) {
        User user = userRepository.findByUsernameOrEmail(usernameOrEmail)
            .orElseThrow(() -> new UnauthorizedException("Invalid username/email or password"));

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new AccountLockedException(
                "Account is temporarily locked due to repeated failed login attempts. Try again later.");
        }

        if (!user.isEnabled()) {
            throw new UnauthorizedException("This account has been disabled");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            // Checkpoint E: authFailureRecorder.recordFailure(...) runs in
            // its own REQUIRES_NEW transaction and has already committed
            // the counter/lockout/AuthFailureEvent write by the time this
            // call returns - independent of this method's own transaction,
            // which is about to be rolled back by the UnauthorizedException
            // thrown below (default rollback rule for unchecked exceptions).
            // See AuthFailureRecorder's Javadoc for the full rationale.
            DetectionContext context = authFailureRecorder.recordFailure(user.getId(), sourceIp);

            // Detection runs AFTER that commit, deliberately not inside
            // AuthFailureRecorder's own transactional method: DetectionEngine
            // .evaluate() is itself @Transactional and would otherwise join
            // (not replace) that same REQUIRES_NEW transaction, so any
            // exception surfacing from it would mark that shared transaction
            // rollback-only - undoing the very counter/lockout/event write
            // this wiring exists to preserve. Calling it here instead, after
            // that transaction has already committed, means a detection
            // failure can only affect detection: it never threatens the
            // already-durable failed-attempt bookkeeping, and (via the
            // catch below) never changes the UnauthorizedException the
            // caller is about to receive.
            if (context != null) {
                try {
                    detectionEngine.evaluate(context);
                } catch (RuntimeException detectionFailure) {
                    log.error("Detection engine failed while evaluating an AUTH_FAILURE event for user {}. "
                        + "The failed-attempt counter, lockout, and AuthFailureEvent record were already "
                        + "committed and are unaffected.", user.getId(), detectionFailure);
                }
            }

            throw new UnauthorizedException("Invalid username/email or password");
        }

        // Successful login clears any prior failure streak.
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String tokenHash = TokenHasher.sha256Hex(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(tokenHash)
            .filter(RefreshToken::isActive)
            .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid or expired"));

        // Rotation-on-use: the presented token is immediately revoked and a
        // brand new one issued, so a stolen-but-unused refresh token can
        // only ever be used once before both the thief and the legitimate
        // holder discover it's dead.
        existing.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existing);

        return issueTokens(existing.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String tokenHash = TokenHasher.sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    private AuthResponse issueTokens(User user) {
        List<String> roleNames = user.getRoles().stream().map(r -> r.getName()).toList();
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), roleNames);

        String rawRefreshToken = TokenHasher.generateOpaqueToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(TokenHasher.sha256Hex(rawRefreshToken));
        refreshToken.setExpiresAt(Instant.now().plusSeconds(jwtService.getRefreshTokenTtlSeconds()));
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            roleNames,
            accessToken,
            rawRefreshToken,
            jwtService.getAccessTokenTtlSeconds()
        );
    }
}
