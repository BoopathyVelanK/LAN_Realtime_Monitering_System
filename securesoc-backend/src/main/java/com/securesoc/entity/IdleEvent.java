package com.securesoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Mirrors collector.get_idle_seconds() - currently only sent when the
 * agent runs where that returns non-null (Windows, once
 * GetLastInputInfo is wired up per collector.py's docstring). */
@Entity
@Table(name = "idle_events")
@Getter
@Setter
@NoArgsConstructor
public class IdleEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private EndpointDevice endpoint;

    @Column(name = "idle_seconds", nullable = false)
    private Integer idleSeconds;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();
}
