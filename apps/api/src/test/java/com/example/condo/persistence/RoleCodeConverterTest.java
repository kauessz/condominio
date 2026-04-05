package com.example.condo.persistence;

import com.example.condo.security.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleCodeConverterTest {

    private final RoleCodeConverter converter = new RoleCodeConverter();

    @Test
    void shouldNormalizeLegacyStaffToPortaria() {
        assertEquals(Role.PORTARIA, converter.convertToEntityAttribute("STAFF"));
    }

    @Test
    void shouldNormalizeLegacyGuestToMorador() {
        assertEquals(Role.MORADOR, converter.convertToEntityAttribute("GUEST"));
    }
}
