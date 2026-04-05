package com.example.condo.dto.onboarding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para rejeitar uma solicitação de cadastro.
 */
public record RejectRequestDto(

    @NotBlank(message = "Motivo de rejeição é obrigatório")
    @Size(max = 1000)
    String reason
) {}
