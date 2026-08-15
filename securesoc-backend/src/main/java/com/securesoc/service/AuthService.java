package com.securesoc.service;

import com.securesoc.dto.AuthResponse;
import com.securesoc.entity.AuthFailureEvent;
import com.securesoc.entity.RefreshToken;
import com.securesoc.entity.User;
import com.securesoc.exception.AccountLockedException;
import com.securesoc.exception.UnauthorizedException;
import com.securesoc.repository.AuthFailureEventRepository;
import com.securesoc.repository.RefreshTokenRepository;
import com.securesoc.repository.UserRepository;
import com.securesoc.security.JwtService;
import com.securesoc.security.TokenHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    // Locks an account for 5 minutes after 5 consecutive failed attempts -
    // mirrors the same protection the agent's registration secret doesn't
    // need (that's a shared machine secret, not a per-human account) but a
    // human login absolutely does.
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 5;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthFailureEventRepository authFailureEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        AuthFailureEventRepository authFailureEventRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authFailureEventRepository = authFailureEventRepository;
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
            registerFailedAttempt(user, sourceIp);
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

    /** Unchanged counter/lockout logic, PLUS (new) one AuthFailureEvent row
     * per call - see V5__phase4_detection_foundation.sql and
     * AuthFailureEvent's Javadoc for why. Fires on exactly the same
     * condition this method already fired on before (wrong password for a
     * known, unlocked, enabled user) - no new trigger paths added. */
    private void registerFailedAttempt(User user, String sourceIp) {
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
