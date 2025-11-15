package com.example.condo.dto.visitor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * DTO para atualizar um visitante existente.
 * Todos os campos são opcionais (atualização parcial).
 */
public record UpdateVisitorRequest(

    @Size(max = 200, message = "Nome deve ter no máximo 200 caracteres")
    String name,

    @Size(max = 50, message = "Documento deve ter no máximo 50 caracteres")
    String document,

    @Size(max = 20, message = "Placa deve ter no máximo 20 caracteres")
    String plate,

    @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
    String phone,

    @Email(message = "Email deve ser válido")
    @Size(max = 200, message = "Email deve ter no máximo 200 caracteres")
    String email,

    @Size(max = 1000, message = "Observação deve ter no máximo 1000 caracteres")
    String note,

    @Size(max = 100, message = "Transportadora deve ter no máximo 100 caracteres")
    String carrier,

    Integer packages,

    Instant expectedInAt,

    Instant expectedOutAt,

    String type
) {
}
