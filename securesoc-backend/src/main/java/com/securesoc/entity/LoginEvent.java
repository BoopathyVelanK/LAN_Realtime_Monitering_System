package com.securesoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Mirrors the payload sent once at agent startup by
 * securesoc-agent/agent.py's send_login_event(). */
@Entity
@Table(name = "login_events")
@Getter
@Setter
@NoArgsConstructor
public class LoginEvent {

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

    @Column(name = "login_time", nullable = false)
    private Instant loginTime;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();
}
