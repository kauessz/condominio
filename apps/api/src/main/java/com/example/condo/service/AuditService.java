package com.example.condo.service;

import com.example.condo.entity.AuditLog;
import com.example.condo.repo.AuditLogRepository;
import com.example.condo.repo.UserRepository;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    @Transactional
    public void log(String action, String entityName, Object entityId, Long condominiumId, Object before, Object after) {
        log(action, entityName, entityId, condominiumId, before, after, null);
    }

    @Transactional
    public void log(String action, String entityName, Object entityId, Long condominiumId, Object before, Object after, Object details) {
        AuditLog entry = new AuditLog();
        UserContext.Data ctx = UserContext.get();

        entry.setTenantId(TenantContext.get());
        entry.setCondominiumId(condominiumId);
        entry.setActorUserId(ctx != null ? ctx.userId() : null);
        entry.setActorRole(ctx != null ? ctx.role() : null);
        if (ctx != null && ctx.userId() != null) {
            userRepository.findByTenantIdAndId(TenantContext.get(), ctx.userId()).ifPresent(actor -> {
                entry.setActorName(actor.getName());
                entry.setActorEmail(actor.getEmail());
            });
        }
        entry.setEntityName(entityName);
        entry.setEntityId(String.valueOf(entityId));
        entry.setAction(action);
        entry.setBeforeState(toJson(before));
        entry.setAfterState(toJson(after));
        entry.setDetails(toJson(details));
        entry.setCreatedAt(Instant.now());

        auditLogRepository.save(entry);
    }

    private JsonNode toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.valueToTree(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Falha ao serializar evento de auditoria", e);
        }
    }
}
