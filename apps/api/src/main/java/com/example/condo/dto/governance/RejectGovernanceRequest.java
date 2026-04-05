package com.example.condo.dto.governance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectGovernanceRequest(
    @NotBlank(message = "Motivo é obrigatório")
    @Size(max = 1000, message = "Motivo deve ter no máximo 1000 caracteres")
    String reason
) {
}
