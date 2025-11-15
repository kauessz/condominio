package com.example.condo.dto.condominium;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para criar um novo condomínio.
 */
public record CreateCondominiumRequest(

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 3, max = 200, message = "Nome deve ter entre 3 e 200 caracteres")
    String name,

    @Pattern(
        regexp = "^\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}$",
        message = "CNPJ deve estar no formato XX.XXX.XXX/XXXX-XX"
    )
    String cnpj
) {
}
