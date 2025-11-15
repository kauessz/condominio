package com.example.condo.dto.auth;

import com.example.condo.security.Role;

/**
 * DTO para resposta de login bem-sucedido.
 */
public record LoginResponse(
    String token,
    String type, // "Bearer"
    String email,
    String name,
    Role role,
    String tenant
) {

    public static LoginResponse of(String token, String email, String name, Role role, String tenant) {
        return new LoginResponse(token, "Bearer", email, name, role, tenant);
    }
}
