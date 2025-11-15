package com.example.condo.dto.unit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO para criar uma nova unidade.
 */
public record CreateUnitRequest(

    @NotNull(message = "ID do condomínio é obrigatório")
    Long condominiumId,

    @Size(max = 50, message = "Código deve ter no máximo 50 caracteres")
    String code,

    @NotBlank(message = "Número é obrigatório")
    @Size(max = 20, message = "Número deve ter no máximo 20 caracteres")
    String number,

    @Size(max = 20, message = "Bloco deve ter no máximo 20 caracteres")
    String block
) {
}
