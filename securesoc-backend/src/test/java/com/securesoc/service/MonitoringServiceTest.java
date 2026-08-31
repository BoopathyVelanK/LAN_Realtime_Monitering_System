package com.securesoc.service;

import com.securesoc.detection.DetectionContext;
import com.securesoc.detection.DetectionEngine;
import com.securesoc.dto.monitoring.MonitoringIngestResponse;
import com.securesoc.dto.monitoring.UsbEventRequest;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.UsbEvent;
import com.securesoc.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitoringServiceTest {

    @Mock private LoginEventRepository loginEventRepository;
    @Mock private LogoutEventRepository logoutEventRepository;
    @Mock private RunningAppSnapshotRepository runningAppSnapshotRepository;
    @Mock private UsbEventRepository usbEventRepository;
    @Mock private VpnEventRepository vpnEventRepository;
    @Mock private IdleEventRepository idleEventRepository;
    @Mock private NetworkUsageEventRepository networkUsageEventRepository;
    @Mock private InternetUsageEventRepository internetUsageEventRepository;
    @Mock private DetectionEngine detectionEngine;

    private MonitoringService monitoringService;

    @BeforeEach
    void setUp() {
        monitoringService = new MonitoringService(
            loginEventRepository,
            logoutEventRepository,
            runningAppSnapshotRepository,
            usbEventRepository,
            vpnEventRepository,
            idleEventRepository,
            networkUsageEventRepository,
            internetUsageEventRepository,
            detectionEngine
        );
    }

    @Test
    void recordUsb_persistsEventAndCallsDetectionEngine_withCorrectContext() {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());
        device.setHostname("test-host");

        UsbEventRequest request = new UsbEventRequest("test-device", "test-id", "vid", "pid", "CONNECTED");

        MonitoringIngestResponse response = monitoringService.recordUsb(device, request);
        
        assertEquals("USB event recorded.", response.message());

        ArgumentCaptor<UsbEvent> eventCaptor = ArgumentCaptor.forClass(UsbEvent.class);
        verify(usbEventRepository).saveAndFlush(eventCaptor.capture());
        
        UsbEvent persistedEvent = eventCaptor.getValue();
        assertEquals(device, persistedEvent.getEndpoint());
        assertEquals("test-device", persistedEvent.getDeviceName());
        assertEquals("test-id", persistedEvent.getDeviceId());
        assertEquals("vid", persistedEvent.getVendorId());
        assertEquals("pid", persistedEvent.getProductId());
        assertEquals(UsbEvent.Action.CONNECTED, persistedEvent.getAction());
        assertNotNull(persistedEvent.getEventTime());

        ArgumentCaptor<DetectionContext> contextCaptor = ArgumentCaptor.forClass(DetectionContext.class);
        verify(detectionEngine).evaluate(contextCaptor.capture());
        
        DetectionContext context = contextCaptor.getValue();
        assertEquals("USB_EVENT", context.eventSource());
        assertEquals(device.getId(), context.endpointId());
        assertNull(context.userId());
        assertEquals(persistedEvent.getEventTime(), context.occurredAt());
        assertEquals(persistedEvent, context.event());
    }
}
