package com.example.condo.dto.visitor;

import com.example.condo.entity.Visitor;

import java.time.Instant;

/**
 * DTO para resposta de visitante.
 */
public record VisitorResponse(
    Long id,
    Long condominiumId,
    Long unitId,
    String unitCode,
    String unitNumber,
    String unitBlock,
    String name,
    String document,
    String plate,
    String phone,
    String email,
    String note,
    String carrier,
    Integer packages,
    Instant checkInAt,
    Instant checkOutAt,
    Instant expectedInAt,
    Instant expectedOutAt,
    String status,
    String type,
    Instant approvedAt,
    String approvedBy,
    String rejectionReason
) {

    /**
     * Converte entidade para DTO (sem dados da unidade).
     */
    public static VisitorResponse from(Visitor visitor) {
        return new VisitorResponse(
            visitor.getId(),
            visitor.getCondominiumId(),
            visitor.getUnitId(),
            null,
            null,
            null,
            visitor.getName(),
            visitor.getDocument(),
            visitor.getPlate(),
            visitor.getPhone(),
            visitor.getEmail(),
            visitor.getNote(),
            visitor.getCarrier(),
            visitor.getPackages(),
            visitor.getCheckInAt(),
            visitor.getCheckOutAt(),
            visitor.getExpectedInAt(),
            visitor.getExpectedOutAt(),
            visitor.getStatus() != null ? visitor.getStatus().name() : null,
            visitor.getType() != null ? visitor.getType().name() : null,
            visitor.getApprovedAt(),
            visitor.getApprovedBy() != null ? visitor.getApprovedBy().toString() : null,
            visitor.getRejectionReason()
        );
    }

    /**
     * Converte entidade para DTO com dados da unidade.
     */
    public static VisitorResponse withUnit(
        Visitor visitor,
        String unitCode,
        String unitNumber,
        String unitBlock
    ) {
        return new VisitorResponse(
            visitor.getId(),
            visitor.getCondominiumId(),
            visitor.getUnitId(),
            unitCode,
            unitNumber,
            unitBlock,
            visitor.getName(),
            visitor.getDocument(),
            visitor.getPlate(),
            visitor.getPhone(),
            visitor.getEmail(),
            visitor.getNote(),
            visitor.getCarrier(),
            visitor.getPackages(),
            visitor.getCheckInAt(),
            visitor.getCheckOutAt(),
            visitor.getExpectedInAt(),
            visitor.getExpectedOutAt(),
            visitor.getStatus() != null ? visitor.getStatus().name() : null,
            visitor.getType() != null ? visitor.getType().name() : null,
            visitor.getApprovedAt(),
            visitor.getApprovedBy() != null ? visitor.getApprovedBy().toString() : null,
            visitor.getRejectionReason()
        );
    }
}