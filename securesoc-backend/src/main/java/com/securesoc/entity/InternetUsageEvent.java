package com.securesoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Mirrors the "internet" half of collector.NetworkUsageTracker.sample() -
 * the same byte delta expressed in MB plus the sampling window length. */
@Entity
@Table(name = "internet_usage_events")
@Getter
@Setter
@NoArgsConstructor
public class InternetUsageEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private EndpointDevice endpoint;

    @Column(name = "upload_mb", nullable = false, precision = 12, scale = 3)
    private BigDecimal uploadMb = BigDecimal.ZERO;

    @Column(name = "download_mb", nullable = false, precision = 12, scale = 3)
    private BigDecimal downloadMb = BigDecimal.ZERO;

    @Column(name = "period_seconds", nullable = false)
    private Integer periodSeconds = 0;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();
}
