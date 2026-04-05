package com.example.condo.dto.unit;

import jakarta.validation.constraints.Size;

/**
 * DTO para atualizar uma unidade existente.
 * Todos os campos são opcionais (atualização parcial).
 */
public record UpdateUnitRequest(

    @Size(max = 50, message = "Código deve ter no máximo 50 caracteres")
    String code,

    @Size(max = 20, message = "Número deve ter no máximo 20 caracteres")
    String number,

    @Size(max = 20, message = "Bloco deve ter no máximo 20 caracteres")
    String block
) {
}
