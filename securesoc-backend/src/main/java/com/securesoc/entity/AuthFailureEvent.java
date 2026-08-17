package com.securesoc.entity;

import com.securesoc.detection.DetectionEvent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One row per failed portal login attempt (see AuthService.
 * registerFailedAttempt). Purely additive alongside the existing
 * users.failed_login_attempts counter / locked_until lockout - that
 * existing logic is unchanged. This table exists so a future detection
 * rule can query "N failures in a time window" per user, which a bare
 * counter that resets on the next successful login can't answer. */
@Entity
@Table(name = "auth_failure_events")
@Getter
@Setter
@NoArgsConstructor
public class AuthFailureEvent implements DetectionEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Best-effort - see AuthController for how this is captured
     * (request.getRemoteAddr()); not reliable behind a reverse proxy
     * without X-Forwarded-For handling, which isn't set up yet. */
    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt = Instant.now();
}
