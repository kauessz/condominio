package com.example.condo.dto.onboarding;

import com.example.condo.entity.CondominiumRequest;

import java.time.Instant;

/**
 * DTO de resposta para solicitações de cadastro (painel do SUPERUSER).
 */
public record CondominiumRequestResponse(
    Long id,
    String condominiumName,
    String cnpj,
    String address,
    String requesterName,
    String requesterEmail,
    String requesterPhone,
    String requesterRole,
    String status,
    String rejectionReason,
    Instant createdAt,
    Instant reviewedAt,
    Long reviewedBy
) {
    public static CondominiumRequestResponse from(CondominiumRequest r) {
        return new CondominiumRequestResponse(
            r.getId(),
            r.getCondominiumName(),
            r.getCnpj(),
            r.getAddress(),
            r.getRequesterName(),
            r.getRequesterEmail(),
            r.getRequesterPhone(),
            r.getRequesterRole(),
            r.getStatus().name(),
            r.getRejectionReason(),
            r.getCreatedAt(),
            r.getReviewedAt(),
            r.getReviewedBy()
        );
    }
}
