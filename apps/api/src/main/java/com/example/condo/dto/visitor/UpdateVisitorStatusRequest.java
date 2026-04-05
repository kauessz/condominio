package com.example.condo.dto.visitor;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO para atualizar o status de um visitante via PATCH /visitors/{id}/status.
 *
 * Transições permitidas:
 * - PENDING   → APPROVED  (morador aprova / admin aprova)
 * - PENDING   → REJECTED  (morador nega / admin nega) — requer reason
 * - APPROVED  → CHECKED_IN (portaria confirma entrada)
 * - CHECKED_IN → CHECKED_OUT (portaria registra saída)
 * - APPROVED  → CHECKED_OUT (saída direta sem CHECKED_IN, compatibilidade)
 *
 * Regras por role:
 * - MORADOR:  pode transicionar PENDING→APPROVED ou PENDING→REJECTED
 *             somente para visitantes da sua própria unidade
 * - PORTARIA: pode transicionar APPROVED→CHECKED_IN, *→CHECKED_OUT
 * - ADMIN/SINDICO/SUPERUSER: qualquer transição
 */
public record UpdateVisitorStatusRequest(

    @NotBlank(message = "Status é obrigatório")
    String status,

    /** Motivo da rejeição — obrigatório quando status = REJECTED */
    String reason
) {
}
