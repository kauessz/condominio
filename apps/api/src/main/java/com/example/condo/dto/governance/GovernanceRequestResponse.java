package com.example.condo.dto.governance;

import com.example.condo.entity.GovernanceRequest;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record GovernanceRequestResponse(
    Long id,
    String requestType,
    String targetEntityType,
    Long targetEntityId,
    Long condominiumId,
    Long requestedByUserId,
    String requestedByRole,
    String status,
    JsonNode payloadBefore,
    JsonNode payloadAfter,
    Long approvedByUserId,
    Instant approvedAt,
    String rejectionReason,
    Instant createdAt,
    Instant updatedAt
) {
    public static GovernanceRequestResponse from(GovernanceRequest request) {
        return new GovernanceRequestResponse(
            request.getId(),
            request.getRequestType().name(),
            request.getTargetEntityType().name(),
            request.getTargetEntityId(),
            request.getCondominiumId(),
            request.getRequestedByUserId(),
            request.getRequestedByRole(),
            request.getStatus().name(),
            request.getPayloadBefore(),
            request.getPayloadAfter(),
            request.getApprovedByUserId(),
            request.getApprovedAt(),
            request.getRejectionReason(),
            request.getCreatedAt(),
            request.getUpdatedAt()
        );
    }
}
