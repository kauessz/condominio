package com.example.condo.persistence;

import com.example.condo.security.Role;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * Mantém compatibilidade com roles legadas persistidas no banco.
 * Isso evita falhas de leitura do JPA quando ainda existem registros antigos.
 */
@Converter(autoApply = false)
public class RoleCodeConverter implements AttributeConverter<Role, String> {

    @Override
    public String convertToDatabaseColumn(Role attribute) {
        return attribute != null ? attribute.name() : null;
    }

    @Override
    public Role convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }

        return switch (dbData.trim().toUpperCase(Locale.ROOT)) {
            case "SUPER_ADMIN", "SUPERADMIN", "SUPERUSER" -> Role.SUPERUSER;
            case "ADMIN" -> Role.ADMIN;
            case "MANAGER", "SINDICO" -> Role.SINDICO;
            case "FINANCEIRO", "FINANCE" -> Role.FINANCEIRO;
            case "OPERADOR", "OPERATOR" -> Role.OPERADOR;
            case "STAFF", "PORTARIA" -> Role.PORTARIA;
            case "RESIDENT", "GUEST", "MORADOR" -> Role.MORADOR;
            case "ZELADOR" -> Role.ZELADOR;
            default -> throw new IllegalArgumentException("Role inválida no banco: " + dbData);
        };
    }
}
