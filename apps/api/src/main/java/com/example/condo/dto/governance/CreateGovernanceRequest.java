package com.example.condo.dto.governance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGovernanceRequest(
    @NotBlank(message = "requestType é obrigatório")
    String requestType,

    Long targetEntityId,

    Long condominiumId,

    Object payloadAfter,

    @Size(max = 1000, message = "Justificativa deve ter no máximo 1000 caracteres")
    String justification
) {
}
