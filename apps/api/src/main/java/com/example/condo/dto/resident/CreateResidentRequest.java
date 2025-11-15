package com.example.condo.dto.resident;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO para criar um novo morador.
 */
public record CreateResidentRequest(

    @NotNull(message = "ID do condomínio é obrigatório")
    Long condominiumId,

    @NotNull(message = "ID da unidade é obrigatório")
    Long unitId,

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 3, max = 200, message = "Nome deve ter entre 3 e 200 caracteres")
    String name,

    @Email(message = "Email deve ser válido")
    @Size(max = 200, message = "Email deve ter no máximo 200 caracteres")
    String email,

    @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
    String phone
) {
}
