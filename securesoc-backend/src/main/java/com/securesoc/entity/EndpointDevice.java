package com.securesoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "endpoint_devices")
@Getter
@Setter
@NoArgsConstructor
public class EndpointDevice {

    public enum Status { ONLINE, OFFLINE }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 150)
    private String hostname;

    @Column(name = "mac_address", nullable = false, unique = true, length = 17)
    private String macAddress;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_id")
    private Laboratory lab;

    @Column(name = "os_name", length = 100)
    private String osName;

    @Column(name = "os_version", length = 100)
    private String osVersion;

    @Column(name = "cpu_info", length = 150)
    private String cpuInfo;

    @Column(name = "ram_mb")
    private Integer ramMb;

    @Column(name = "disk_gb")
    private Integer diskGb;

    @Column(name = "agent_version", length = 50)
    private String agentVersion;

    /** SHA-256 hash of the agent's bearer token - see RefreshToken's Javadoc
     * for the same rationale (never store the raw secret). */
    @Column(name = "agent_token_hash", nullable = false, unique = true, length = 64)
    private String agentTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OFFLINE;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
