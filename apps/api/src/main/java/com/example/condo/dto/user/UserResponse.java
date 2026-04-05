package com.example.condo.dto.user;

import com.example.condo.entity.User;

import java.time.LocalDateTime;

/**
 * DTO de resposta para usuário.
 * Não expõe passwordHash.
 */
public record UserResponse(
    Long id,
    String name,
    String email,
    String role,
    String roleLabel,
    String condominiumName,
    Long condominiumId,
    Long unitId,
    boolean mustChangePassword,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getName() != null ? user.getName() : user.getEmail(),
            user.getEmail(),
            user.getRole() != null ? user.getRole().name() : null,
            user.getRole() != null ? user.getRole().getDisplayName() : null,
            null,
            user.getCondominiumId(),
            user.getUnitId(),
            user.isMustChangePassword(),
            user.getCreatedAt(),
            null
        );
    }
}
