package com.securesoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Ingest target for POST /monitoring/usb. collector.get_usb_devices_stub()
 * currently always returns an empty list - real WMI-based hotplug events
 * are wired up once the agent runs on real Windows hardware (see
 * collector.py's docstring) - but this table and endpoint are ready now. */
@Entity
@Table(name = "usb_events")
@Getter
@Setter
@NoArgsConstructor
public class UsbEvent {

    public enum Action { CONNECTED, DISCONNECTED }

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private EndpointDevice endpoint;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "vendor_id", length = 20)
    private String vendorId;

    @Column(name = "product_id", length = 20)
    private String productId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Action action;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime = Instant.now();

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();
}
