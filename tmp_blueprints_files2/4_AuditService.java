package com.condohub.audit.service;

import com.condohub.audit.entity.AuditLog;
import com.condohub.audit.repository.AuditLogRepository;
import com.condohub.auth.model.AppUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * AuditService — grava eventos operacionais no canal de auditoria.
 *
 * Uso:
 *   auditService.log(condominiumId, currentUser, "Financeiro", "Ação", "Descrição detalhada");
 *
 * currentUser pode ser null para eventos do sistema (jobs agendados, webhooks).
 * Operação é @Async para não bloquear a transação principal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Grava um evento de auditoria de forma assíncrona.
     *
     * @param condominiumId  ID do condomínio (tenant) — NUNCA do body, sempre do contexto
     * @param actor          Usuário que executou a ação (null = sistema)
     * @param module         Nome do módulo: "Financeiro", "Visitantes", "Reservas", etc.
     * @param action         Ação resumida: "Geração de cobranças", "Mudança de status", etc.
     * @param description    Descrição detalhada com contexto (unidade, valores, ids)
     */
    @Async
    public void log(Long condominiumId,
                    AppUserDetails actor,
                    String module,
                    String action,
                    String description) {
        try {
            AuditLog entry = new AuditLog();
            entry.setCondominiumId(condominiumId);
            entry.setModule(module);
            entry.setAction(action);
            entry.setDescription(description);
            entry.setCreatedAt(LocalDateTime.now());

            if (actor != null) {
                entry.setUserId(actor.getId());
                entry.setUserName(actor.getName());
                entry.setUserRole(actor.getRole() != null ? actor.getRole().name() : null);
            } else {
                entry.setUserName("system");
                entry.setUserRole("SYSTEM");
            }

            auditLogRepository.save(entry);

        } catch (Exception e) {
            // Auditoria nunca deve quebrar o fluxo principal
            log.error("[AuditService] Falha ao gravar audit log — module={} action={}: {}",
                    module, action, e.getMessage(), e);
        }
    }
}
