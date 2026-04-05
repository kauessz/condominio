package com.example.condo.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

@Entity
@Table(name = "governance_request")
public class GovernanceRequest {

    public enum RequestType {
        CREATE_CONDOMINIUM,
        UPDATE_CONDOMINIUM,
        DELETE_CONDOMINIUM,
        ACTIVATE_CONDOMINIUM,
        DEACTIVATE_CONDOMINIUM
    }

    public enum TargetEntityType {
        CONDOMINIUM
    }

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 64)
    private RequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_entity_type", nullable = false, length = 64)
    private TargetEntityType targetEntityType;

    @Column(name = "target_entity_id")
    private Long targetEntityId;

    @Column(name = "condominium_id")
    private Long condominiumId;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Column(name = "requested_by_role", nullable = false, length = 32)
    private String requestedByRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Status status = Status.PENDING;

    @Column(name = "payload_before", columnDefinition = "jsonb")
    private JsonNode payloadBefore;

    @Column(name = "payload_after", columnDefinition = "jsonb")
    private JsonNode payloadAfter;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public RequestType getRequestType() { return requestType; }
    public void setRequestType(RequestType requestType) { this.requestType = requestType; }
    public TargetEntityType getTargetEntityType() { return targetEntityType; }
    public void setTargetEntityType(TargetEntityType targetEntityType) { this.targetEntityType = targetEntityType; }
    public Long getTargetEntityId() { return targetEntityId; }
    public void setTargetEntityId(Long targetEntityId) { this.targetEntityId = targetEntityId; }
    public Long getCondominiumId() { return condominiumId; }
    public void setCondominiumId(Long condominiumId) { this.condominiumId = condominiumId; }
    public Long getRequestedByUserId() { return requestedByUserId; }
    public void setRequestedByUserId(Long requestedByUserId) { this.requestedByUserId = requestedByUserId; }
    public String getRequestedByRole() { return requestedByRole; }
    public void setRequestedByRole(String requestedByRole) { this.requestedByRole = requestedByRole; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public JsonNode getPayloadBefore() { return payloadBefore; }
    public void setPayloadBefore(JsonNode payloadBefore) { this.payloadBefore = payloadBefore; }
    public JsonNode getPayloadAfter() { return payloadAfter; }
    public void setPayloadAfter(JsonNode payloadAfter) { this.payloadAfter = payloadAfter; }
    public Long getApprovedByUserId() { return approvedByUserId; }
    public void setApprovedByUserId(Long approvedByUserId) { this.approvedByUserId = approvedByUserId; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
