package com.example.condo.dto.onboarding;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para criação de solicitação de cadastro (formulário público).
 */
public record CondominiumRequestDto(

    @NotBlank(message = "Nome do condomínio é obrigatório")
    @Size(max = 255)
    String condominiumName,

    @Size(max = 18)
    String cnpj,

    String address,

    @NotBlank(message = "Seu nome é obrigatório")
    @Size(max = 255)
    String requesterName,

    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail inválido")
    @Size(max = 255)
    String requesterEmail,

    @Size(max = 20)
    String requesterPhone,

    // "SINDICO", "ADMINISTRADORA", "OUTRO"
    String requesterRole
) {}
