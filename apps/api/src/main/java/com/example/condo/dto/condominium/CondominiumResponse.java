package com.example.condo.dto.condominium;

import com.example.condo.entity.Condominium;

import java.time.Instant;

/**
 * DTO para resposta de condomínio (detalhes).
 */
public record CondominiumResponse(
    Long id,
    String name,
    String cnpj,
    Instant createdAt,
    Long unitCount,
    Long residentCount
) {

    /**
     * Converte entidade para DTO (sem contadores).
     */
    public static CondominiumResponse from(Condominium condominium) {
        return new CondominiumResponse(
            condominium.getId(),
            condominium.getName(),
            condominium.getCnpj(),
            condominium.getCreatedAt(),
            null,
            null
        );
    }

    /**
     * Converte entidade para DTO com contadores.
     */
    public static CondominiumResponse withCounts(
        Condominium condominium,
        Long unitCount,
        Long residentCount
    ) {
        return new CondominiumResponse(
            condominium.getId(),
            condominium.getName(),
            condominium.getCnpj(),
            condominium.getCreatedAt(),
            unitCount,
            residentCount
        );
    }
}
