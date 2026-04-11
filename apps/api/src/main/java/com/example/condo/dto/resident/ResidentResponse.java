package com.example.condo.dto.resident;

import com.example.condo.entity.Resident;

/**
 * DTO para resposta de morador.
 *
 * unitDisplay é um campo computado para facilitar a exibição no frontend:
 *   ex: "101 - Bloco A"
 */
public record ResidentResponse(
    Long id,
    Long condominiumId,
    Long unitId,
    String unitCode,
    String unitNumber,
    String unitBlock,
    String unitDisplay,
    String name,
    String email,
    String phone,
    String cpf,
    Long userId,
    boolean hasAccount,
    String accessRole
) {

    /**
     * Computa a label de exibição da unidade.
     * Ex: "101 - Bloco A", "202", ou null se sem dados de unidade.
     */
    private static String buildUnitDisplay(String unitNumber, String unitBlock) {
        if (unitNumber == null && unitBlock == null) return null;
        StringBuilder sb = new StringBuilder();
        if (unitNumber != null) sb.append(unitNumber);
        if (unitBlock != null && !unitBlock.isBlank()) {
            if (!sb.isEmpty()) sb.append(" - Bloco ");
            sb.append(unitBlock);
        }
        return sb.toString();
    }

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
            null,
            resident.getName(),
            resident.getEmail(),
            resident.getPhone(),
            resident.getCpf(),
            resident.getUserId(),
            resident.getUserId() != null,
            null
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
            buildUnitDisplay(unitNumber, unitBlock),
            resident.getName(),
            resident.getEmail(),
            resident.getPhone(),
            resident.getCpf(),
            resident.getUserId(),
            resident.getUserId() != null,
            null
        );
    }

    public static ResidentResponse withAccount(ResidentResponse base, String accessRole) {
        return new ResidentResponse(
            base.id(),
            base.condominiumId(),
            base.unitId(),
            base.unitCode(),
            base.unitNumber(),
            base.unitBlock(),
            base.unitDisplay(),
            base.name(),
            base.email(),
            base.phone(),
            base.cpf(),
            base.userId(),
            base.hasAccount(),
            accessRole
        );
    }
}
