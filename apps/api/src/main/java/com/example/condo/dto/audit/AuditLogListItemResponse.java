package com.example.condo.dto.audit;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record AuditLogListItemResponse(
    Long id,
    Instant createdAt,
    String module,
    String action,
    String entityType,
    String entityId,
    String description,
    Long actorUserId,
    String actorName,
    String actorEmail,
    String actorRole,
    Long condominiumId,
    String condominiumName,
    JsonNode details
) {
}
