package com.example.condo.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO para criação de usuário.
 *
 * Regras de isolamento (aplicadas no service):
 * - SUPERUSER: condominiumId do request é usado (obrigatório para roles não-SUPERUSER)
 * - Não-SUPERUSER: condominiumId é ignorado e vem do JWT
 *
 * unitId é obrigatório apenas para role MORADOR.
 */
public record CreateUserRequest(

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 2, max = 255, message = "Nome deve ter entre 2 e 255 caracteres")
    String name,

    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail inválido")
    String email,

    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
    String password,

    @NotNull(message = "Role é obrigatória")
    String role,

    /**
     * Condomínio do novo usuário.
     * - SUPERUSER criador: obrigatório para roles não-SUPERUSER
     * - Não-SUPERUSER criador: ignorado — backend usa condominiumId do JWT
     */
    Long condominiumId,

    /**
     * Unidade do novo usuário.
     * Obrigatório apenas quando role = MORADOR ou ZELADOR ou SINDICO.
     */
    Long unitId
) {}
