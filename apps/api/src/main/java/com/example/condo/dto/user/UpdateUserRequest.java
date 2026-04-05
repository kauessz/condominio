package com.example.condo.dto.user;

/**
 * DTO para atualização de role e/ou condomínio de um usuário.
 * Todos os campos são opcionais (atualização parcial).
 *
 * Uso exclusivo de SUPERUSER.
 */
public record UpdateUserRequest(
    /** Nova role do usuário. Null = não alterar. */
    String role,

    /** Novo condomínio. Null = não alterar. Use 0 para remover (SUPERUSER sem condo). */
    Long condominiumId,

    /** Nova unidade. Null = não alterar. */
    Long unitId
) {}
