package com.example.condo.dto.visitor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para rejeitar um visitante.
 */
public record RejectVisitorRequest(

    @NotBlank(message = "Motivo da rejeição é obrigatório")
    @Size(min = 3, max = 500, message = "Motivo deve ter entre 3 e 500 caracteres")
    String reason
) {
}
