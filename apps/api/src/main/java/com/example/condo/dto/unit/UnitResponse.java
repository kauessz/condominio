package com.example.condo.dto.unit;

import com.example.condo.entity.Unit;

import java.time.Instant;

/**
 * DTO para resposta de unidade.
 */
public record UnitResponse(
    Long id,
    Long condominiumId,
    String condominiumName,
    String code,
    String number,
    String block,
    Instant createdAt
) {

    /**
     * Converte entidade para DTO (sem nome do condomínio).
     */
    public static UnitResponse from(Unit unit) {
        return new UnitResponse(
            unit.getId(),
            unit.getCondominiumId(),
            null,
            unit.getCode(),
            unit.getNumber(),
            unit.getBlock(),
            unit.getCreatedAt()
        );
    }

    /**
     * Converte entidade para DTO com nome do condomínio.
     */
    public static UnitResponse withCondominiumName(Unit unit, String condominiumName) {
        return new UnitResponse(
            unit.getId(),
            unit.getCondominiumId(),
            condominiumName,
            unit.getCode(),
            unit.getNumber(),
            unit.getBlock(),
            unit.getCreatedAt()
        );
    }
}
