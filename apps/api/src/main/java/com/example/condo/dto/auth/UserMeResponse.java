package com.example.condo.dto.auth;

import com.example.condo.entity.User;
import com.example.condo.security.Role;

import java.time.Instant;

/**
 * DTO para resposta do endpoint /me (dados do usuário autenticado).
 */
public record UserMeResponse(
    Long id,
    String email,
    String name,
    Role role,
    String tenant,
    Instant createdAt
) {

    public static UserMeResponse from(User user) {
        return new UserMeResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getRole(),
            user.getTenantId(),
            user.getCreatedAt()
        );
    }
}
