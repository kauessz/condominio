package com.example.condo.dto.resident;

import com.example.condo.entity.Resident;

/**
 * DTO para resposta de morador.
 */
public record ResidentResponse(
    Long id,
    Long condominiumId,
    Long unitId,
    String unitCode,
    String unitNumber,
    String unitBlock,
    String name,
    String email,
    String phone
) {

    /**
     * Converte entidade para DTO (sem dados da unidade).
     */
    public static ResidentResponse from(Resident resident) {
        return new ResidentResponse(
            resident.getId(),
            resident.getCondominiumId(),
            resident.getUnitId(),
            null,
            null,
            null,
            resident.getName(),
            resident.getEmail(),
            resident.getPhone()
        );
    }

    /**
     * Converte entidade para DTO com dados da unidade.
     */
    public static ResidentResponse withUnit(
        Resident resident,
        String unitCode,
        String unitNumber,
        String unitBlock
    ) {
        return new ResidentResponse(
            resident.getId(),
            resident.getCondominiumId(),
            resident.getUnitId(),
            unitCode,
            unitNumber,
            unitBlock,
            resident.getName(),
            resident.getEmail(),
            resident.getPhone()
        );
    }
}
