package com.example.condo.service;

import com.example.condo.audit.AuditAction;
import com.example.condo.audit.AuditModule;
import com.example.condo.dto.audit.AuditLogListItemResponse;
import com.example.condo.dto.common.PageResponse;
import com.example.condo.entity.AuditLog;
import com.example.condo.entity.Condominium;
import com.example.condo.repo.AuditLogRepository;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.UserRepository;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final CondominiumRepository condominiumRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public AuditService(
        AuditLogRepository auditLogRepository,
        CondominiumRepository condominiumRepository,
        ObjectMapper objectMapper,
        UserRepository userRepository
    ) {
        this.auditLogRepository = auditLogRepository;
        this.condominiumRepository = condominiumRepository;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    @Transactional
    public void log(String action, String entityName, Object entityId, Long condominiumId, Object before, Object after) {
        log(action, entityName, entityId, condominiumId, before, after, null);
    }

    @Transactional
    public void log(String action, String entityName, Object entityId, Long condominiumId, Object before, Object after, Object details) {
        save(
            resolveModule(entityName),
            sanitize(action),
            entityName,
            entityId,
            condominiumId,
            buildDefaultDescription(action, entityName, entityId),
            before,
            after,
            details
        );
    }

    @Transactional
    public void log(
        AuditModule module,
        AuditAction action,
        String entityName,
        Object entityId,
        Long condominiumId,
        String description,
        Object before,
        Object after,
        Object details
    ) {
        save(module != null ? module.name() : null, action.name(), entityName, entityId, condominiumId, description, before, after, details);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogListItemResponse> list(
        String module,
        Long condominiumId,
        Long actorUserId,
        String actor,
        String query,
        Instant from,
        Instant to,
        int page,
        int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        String tenantId = TenantContext.get();
        Long scopedCondominiumId = resolveAuditScopeCondominiumId(condominiumId);

        Specification<AuditLog> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (scopedCondominiumId != null) {
                predicates.add(cb.equal(root.get("condominiumId"), scopedCondominiumId));
            }
            if (actorUserId != null) {
                predicates.add(cb.equal(root.get("actorUserId"), actorUserId));
            }
            if (actor != null && !actor.isBlank()) {
                String actorLike = "%" + actor.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(cb.coalesce(root.get("actorName").as(String.class), "")), actorLike),
                    cb.like(cb.lower(cb.coalesce(root.get("actorEmail").as(String.class), "")), actorLike)
                ));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (module != null && !module.isBlank()) {
                String normalizedModule = module.trim().toUpperCase(Locale.ROOT);
                List<Predicate> modulePredicates = new ArrayList<>();
                modulePredicates.add(cb.equal(cb.upper(root.get("module")), normalizedModule));
                for (String entityName : legacyEntityNamesForModule(normalizedModule)) {
                    modulePredicates.add(cb.equal(root.get("entityName"), entityName));
                }
                predicates.add(cb.or(modulePredicates.toArray(Predicate[]::new)));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(cb.coalesce(root.get("description").as(String.class), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("actorName").as(String.class), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("actorEmail").as(String.class), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("entityName").as(String.class), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("action").as(String.class), "")), like)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<AuditLog> auditPage = auditLogRepository.findAll(spec, pageable);
        Map<Long, String> condominiumNames = resolveCondominiumNames(
            auditPage.getContent().stream().map(AuditLog::getCondominiumId).collect(Collectors.toSet())
        );

        Page<AuditLogListItemResponse> mapped = auditPage.map(log -> new AuditLogListItemResponse(
            log.getId(),
            log.getCreatedAt(),
            resolvedModuleName(log),
            sanitize(log.getAction()),
            log.getEntityName(),
            log.getEntityId(),
            resolvedDescription(log),
            log.getActorUserId(),
            log.getActorName(),
            log.getActorEmail(),
            log.getActorRole(),
            log.getCondominiumId(),
            condominiumNames.get(log.getCondominiumId()),
            log.getDetails()
        ));
        return PageResponse.of(mapped);
    }

    private void save(
        String module,
        String action,
        String entityName,
        Object entityId,
        Long condominiumId,
        String description,
        Object before,
        Object after,
        Object details
    ) {
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
        entry.setModule(module);
        entry.setEntityName(entityName);
        entry.setEntityId(String.valueOf(entityId));
        entry.setAction(action);
        entry.setDescription(description);
        entry.setBeforeState(toJson(before));
        entry.setAfterState(toJson(after));
        entry.setDetails(toJson(details));
        entry.setCreatedAt(Instant.now());

        auditLogRepository.save(entry);
    }

    private Long resolveAuditScopeCondominiumId(Long requestedCondominiumId) {
        if (UserContext.isSuperuser()) {
            return requestedCondominiumId;
        }
        UserContext.Data ctx = UserContext.get();
        return ctx != null ? ctx.condominiumId() : null;
    }

    private Set<String> legacyEntityNamesForModule(String module) {
        return switch (module) {
            case "VISITORS" -> Set.of("Visitor");
            case "RESERVATIONS" -> Set.of("Reservation", "CommonArea");
            case "ASSEMBLIES" -> Set.of("Assembly", "AssemblyAgendaItem", "AssemblyVote", "AssemblyAgendaOption");
            case "PARKING" -> Set.of("ParkingSpot", "ParkingDraw", "ParkingDrawRegistration", "ParkingSpotAssignment");
            case "FINANCIAL" -> Set.of("Invoice", "FinancialConfig");
            default -> Set.of();
        };
    }

    private Map<Long, String> resolveCondominiumNames(Collection<Long> condominiumIds) {
        Set<Long> ids = condominiumIds.stream().filter(id -> id != null).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return condominiumRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(Condominium::getId, Condominium::getName));
    }

    private String resolvedModuleName(AuditLog log) {
        if (log.getModule() != null && !log.getModule().isBlank()) {
            return log.getModule();
        }
        String resolved = resolveModule(log.getEntityName());
        return resolved != null ? resolved : "SYSTEM";
    }

    private String resolvedDescription(AuditLog log) {
        if (log.getDescription() != null && !log.getDescription().isBlank()) {
            return log.getDescription();
        }
        return buildDefaultDescription(log.getAction(), log.getEntityName(), log.getEntityId());
    }

    private String resolveModule(String entityName) {
        if (entityName == null || entityName.isBlank()) {
            return AuditModule.SYSTEM.name();
        }
        return switch (entityName) {
            case "Visitor" -> AuditModule.VISITORS.name();
            case "Reservation", "CommonArea" -> AuditModule.RESERVATIONS.name();
            case "Assembly", "AssemblyAgendaItem", "AssemblyVote", "AssemblyAgendaOption" -> AuditModule.ASSEMBLIES.name();
            case "ParkingSpot", "ParkingDraw", "ParkingDrawRegistration", "ParkingSpotAssignment" -> AuditModule.PARKING.name();
            case "Invoice", "FinancialConfig" -> AuditModule.FINANCIAL.name();
            case "User" -> AuditModule.USERS.name();
            case "Resident" -> AuditModule.RESIDENTS.name();
            case "Unit" -> AuditModule.UNITS.name();
            case "Condominium" -> AuditModule.CONDOMINIUMS.name();
            default -> AuditModule.SYSTEM.name();
        };
    }

    private String buildDefaultDescription(String action, String entityName, Object entityId) {
        return switch (sanitize(entityName)) {
            case "Visitor" -> "Registro de visitante #" + entityId;
            case "Reservation" -> "Registro de reserva #" + entityId;
            case "Assembly" -> "Registro de assembleia #" + entityId;
            case "AssemblyAgendaItem" -> "Registro de pauta #" + entityId;
            case "AssemblyVote" -> "Registro de voto #" + entityId;
            case "ParkingSpot" -> "Registro de vaga #" + entityId;
            case "ParkingDraw" -> "Registro de sorteio #" + entityId;
            case "ParkingSpotAssignment" -> "Registro de atribuição de vaga #" + entityId;
            case "Invoice" -> "Registro de cobrança #" + entityId;
            case "FinancialConfig" -> "Configuração financeira #" + entityId;
            case "Condominium" -> "Registro de condomínio #" + entityId;
            default -> "Registro de " + sanitize(entityName) + " #" + entityId;
        };
    }

    private String sanitize(String value) {
        return value == null ? "" : value;
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
