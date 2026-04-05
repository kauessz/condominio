package com.example.condo.dto.visitor;

import jakarta.validation.constraints.Size;

/**
 * DTO para aprovar um visitante.
 */
public record ApproveVisitorRequest(

    @Size(max = 1000, message = "Observação deve ter no máximo 1000 caracteres")
    String note
) {

    public static ApproveVisitorRequest empty() {
        return new ApproveVisitorRequest(null);
    }
}
