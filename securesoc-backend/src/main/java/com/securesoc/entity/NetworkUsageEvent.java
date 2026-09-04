package com.securesoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Mirrors the "network" half of collector.NetworkUsageTracker.sample() -
 * raw byte deltas since the previous sample. interfaceName is always null
 * for now (aggregated across all interfaces per collector.py's comment). */
@Entity
@Table(name = "network_usage_events")
@Getter
@Setter
@NoArgsConstructor
public class NetworkUsageEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private EndpointDevice endpoint;

    @Column(name = "bytes_sent", nullable = false)
    private Long bytesSent = 0L;

    @Column(name = "bytes_received", nullable = false)
    private Long bytesReceived = 0L;

    @Column(name = "interface_name", length = 100)
    private String interfaceName;

    /** Agent's own collection timestamp (collector.NetworkUsageTracker.
     * sample()'s sampledAt), as opposed to recordedAt below which is when
     * *this backend* ingested the row. Nullable: historical rows predate
     * this column, and a not-yet-upgraded agent (or a payload already
     * sitting in its offline queue) won't send it either - see
     * MonitoringService.recordNetworkUsage. */
    @Column(name = "sampled_at")
    private Instant sampledAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();
}
