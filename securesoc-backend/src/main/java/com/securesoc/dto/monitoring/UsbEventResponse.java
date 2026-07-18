package com.securesoc.dto.monitoring;

import java.time.Instant;
import java.util.UUID;

/** Read shape for GET /monitoring/usb - mirrors UsbEvent plus the
 * endpoint's hostname. action is the enum name as a string (CONNECTED |
 * DISCONNECTED), or null for the rare row where the agent sent an
 * unrecognized action string - see MonitoringService.recordUsb. */
public record UsbEventResponse(
    UUID id,
    UUID endpointId,
    String hostname,
    String deviceName,
    String deviceId,
    String vendorId,
    String productId,
    String action,
    Instant eventTime,
    Instant receivedAt
) {}
