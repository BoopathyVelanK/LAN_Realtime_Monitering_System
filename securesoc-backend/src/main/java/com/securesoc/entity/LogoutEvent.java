package com.securesoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Mirrors the payload sent once at agent shutdown by
 * securesoc-agent/agent.py's send_logout_event(). */
@Entity
@Table(name = "logout_events")
@Getter
@Setter
@NoArgsConstructor
public class LogoutEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private EndpointDevice endpoint;

    @Column(name = "os_username", nullable = false, length = 150)
    private String osUsername;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "logout_time", nullable = false)
    private Instant logoutTime;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();
}
