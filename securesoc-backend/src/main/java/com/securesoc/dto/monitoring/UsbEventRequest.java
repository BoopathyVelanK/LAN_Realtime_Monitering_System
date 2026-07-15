package com.securesoc.dto.monitoring;

/** Ingest shape for POST /monitoring/usb. collector.get_usb_devices_stub()
 * currently always returns [] so this endpoint isn't exercised by the
 * agent yet - the shape is ready ahead of real WMI hotplug events (see
 * collector.py's docstring), matching device fields typical of such
 * events (deviceName, deviceId, vendorId, productId, action). */
public record UsbEventRequest(
    String deviceName,
    String deviceId,
    String vendorId,
    String productId,
    String action  // CONNECTED | DISCONNECTED
) {}
