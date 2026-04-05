package com.example.condo.dto.auth;

import com.example.condo.entity.User;
import com.example.condo.security.Role;

import java.time.LocalDateTime;

/**
 * DTO para resposta do endpoint GET /api/auth/me.
 */
public record UserMeResponse(
    Long id,
    String email,
    String name,
    Role role,
    String tenant,
    Long unitId,
    Long condominiumId,
    LocalDateTime createdAt
) {

    public static UserMeResponse from(User user) {
        return new UserMeResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getRole(),
            user.getTenantId(),
            user.getUnitId(),
            user.getCondominiumId(),
            user.getCreatedAt()
        );
    }
}
