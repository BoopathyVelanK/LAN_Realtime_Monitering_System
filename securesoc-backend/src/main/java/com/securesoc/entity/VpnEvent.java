package com.securesoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Mirrors collector.detect_vpn_adapters() - one row per adapter found on
 * a monitoring cycle, or a single active=false row when none are. This is
 * a heuristic signal only; deciding whether it's a policy violation is
 * Phase 4 detection-engine work, not this table's concern. */
@Entity
@Table(name = "vpn_events")
@Getter
@Setter
@NoArgsConstructor
public class VpnEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private EndpointDevice endpoint;

    @Column(name = "adapter_name", length = 150)
    private String adapterName;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt = Instant.now();
}
