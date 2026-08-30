package com.securesoc.dto;

import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
    UUID id,
    UUID endpointId,
    String hostname,
    String category,
    String severity,
    String title,
    String description,
    String status,
    UUID assignedToUserId,
    String assignedToUsername,
    UUID acknowledgedByUserId,
    String acknowledgedByUsername,
    Instant acknowledgedAt,
    Instant createdAt,
    Instant updatedAt,
    Instant resolvedAt
) {}
