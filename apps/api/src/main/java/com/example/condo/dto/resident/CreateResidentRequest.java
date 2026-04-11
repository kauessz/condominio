package com.example.condo.dto.resident;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para criar um novo morador.
 */
public record CreateResidentRequest(

    // condominiumId é opcional: para não-SUPERUSER o backend resolve pelo JWT
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
    String phone,

    /** CPF no formato 000.000.000-00 ou apenas 11 dígitos. Campo opcional. */
    @Pattern(
        regexp = "\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}|\\d{11}",
        message = "CPF deve estar no formato 000.000.000-00 ou conter 11 dígitos"
    )
    String cpf,

    Boolean createAccount,

    String accessRole,

    @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
    String password
) {
}
