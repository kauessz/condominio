package com.example.condo.service;

import com.example.condo.dto.resident.CreateResidentRequest;
import com.example.condo.dto.resident.ResidentResponse;
import com.example.condo.dto.resident.UpdateResidentRequest;
import com.example.condo.entity.Resident;
import com.example.condo.entity.User;
import com.example.condo.exception.BusinessException;
import com.example.condo.exception.ResourceNotFoundException;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.ResidentRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.repo.UserRepository;
import com.example.condo.security.Role;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service para operações de moradores.
 *
 * Isolamento de tenant:
 * - SUPERUSER: usa o condominiumId fornecido na requisição.
 * - Outros roles: ignora o condominiumId da requisição e usa o do JWT (UserContext).
 */
@Service
@Transactional(readOnly = true)
public class ResidentService {

    private final ResidentRepository residentRepo;
    private final CondominiumRepository condominiumRepo;
    private final UnitRepository unitRepo;
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public ResidentService(
        ResidentRepository residentRepo,
        CondominiumRepository condominiumRepo,
        UnitRepository unitRepo,
        UserRepository userRepo,
        PasswordEncoder passwordEncoder,
        AuditService auditService
    ) {
        this.residentRepo = residentRepo;
        this.condominiumRepo = condominiumRepo;
        this.unitRepo = unitRepo;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * Lista moradores com paginação e busca.
     *
     * @param condominiumIdParam condominiumId vindo do request (usado apenas para SUPERUSER)
     */
    public Page<ResidentResponse> search(Long condominiumIdParam, String query, Pageable pageable) {
        String tenantId = TenantContext.get();
        Long condominiumId = UserContext.resolveCondominiumId(condominiumIdParam);
        Long scopedUnitId = isMorador() ? UserContext.unitId() : null;

        if (condominiumId == null) {
            return Page.empty(pageable);
        }

        Page<Object[]> page = residentRepo.searchWithUnit(tenantId, condominiumId, scopedUnitId, query, pageable);

        return page.map(row -> {
            Resident resident = (Resident) row[0];
            String unitCode = row.length > 1 ? (String) row[1] : null;
            String unitNumber = row.length > 2 ? (String) row[2] : null;
            String unitBlock = row.length > 3 ? (String) row[3] : null;

            return enrichAccount(ResidentResponse.withUnit(resident, unitCode, unitNumber, unitBlock), tenantId);
        });
    }

    /**
     * Busca morador por ID.
     */
    public ResidentResponse getById(Long id) {
        String tenantId = TenantContext.get();

        Resident resident = residentRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Morador", "id", id));

        // Para não-SUPERUSER, valida que o morador pertence ao condomínio do usuário
        Long effectiveCondoId = UserContext.resolveCondominiumId(resident.getCondominiumId());
        if (effectiveCondoId != null && !effectiveCondoId.equals(resident.getCondominiumId())) {
            throw new ResourceNotFoundException("Morador", "id", id);
        }
        enforceResidentUnitScope(resident);

        // Busca dados da unidade para retornar unitDisplay
        if (resident.getUnitId() != null) {
            return unitRepo.findByTenantIdAndId(tenantId, resident.getUnitId())
                .map(unit -> enrichAccount(ResidentResponse.withUnit(
                    resident, unit.getCode(), unit.getNumber(), unit.getBlock()
                ), tenantId))
                .orElseGet(() -> enrichAccount(ResidentResponse.from(resident), tenantId));
        }

        return enrichAccount(ResidentResponse.from(resident), tenantId);
    }

    /**
     * Cria um novo morador.
     */
    @Transactional
    public ResidentResponse create(CreateResidentRequest request) {
        String tenantId = TenantContext.get();

        // Para não-SUPERUSER, usa condominiumId do JWT e ignora o do request
        Long condominiumId = UserContext.resolveCondominiumId(request.condominiumId());
        if (condominiumId == null) {
            throw new BusinessException("Usuário sem condomínio configurado. Contate o administrador.");
        }

        validateCondominiumExists(tenantId, condominiumId);
        Long unitId = request.unitId();
        if (isMorador()) {
            Long currentUnitId = UserContext.unitId();
            if (currentUnitId == null) {
                throw new BusinessException("Morador autenticado sem unidade vinculada.");
            }
            unitId = currentUnitId;
        }
        validateUnitExists(tenantId, condominiumId, unitId);

        Resident resident = new Resident();
        resident.setTenantId(tenantId);
        resident.setCondominiumId(condominiumId);
        resident.setUnitId(unitId);
        resident.setName(request.name().trim());
        resident.setEmail(request.email() != null ? request.email().trim() : null);
        resident.setPhone(request.phone() != null ? request.phone().trim() : null);
        resident.setCpf(normalizeCpf(request.cpf()));
        maybeCreateLinkedUser(tenantId, resident, request.createAccount(), request.accessRole(), request.password());

        resident = residentRepo.save(resident);
        ResidentResponse after = enrichAccount(ResidentResponse.from(resident), tenantId);
        auditService.log("CREATE", "Resident", resident.getId(), resident.getCondominiumId(), null, after, residentAuditDetails(resident, after));

        return after;
    }

    /**
     * Atualiza um morador existente.
     */
    @Transactional
    public ResidentResponse update(Long id, UpdateResidentRequest request) {
        String tenantId = TenantContext.get();

        Resident resident = residentRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Morador", "id", id));
        ResidentResponse before = enrichAccount(ResidentResponse.from(resident), tenantId);

        // Para não-SUPERUSER, garante que o morador pertence ao condomínio do usuário
        Long effectiveCondoId = UserContext.resolveCondominiumId(resident.getCondominiumId());
        if (effectiveCondoId != null && !effectiveCondoId.equals(resident.getCondominiumId())) {
            throw new ResourceNotFoundException("Morador", "id", id);
        }
        enforceResidentUnitScope(resident);

        if (isMorador() && request.unitId() != null && !request.unitId().equals(resident.getUnitId())) {
            throw new BusinessException("Morador não pode alterar a unidade de outro morador");
        }

        if (request.unitId() != null && !request.unitId().equals(resident.getUnitId())) {
            validateUnitExists(tenantId, resident.getCondominiumId(), request.unitId());
            resident.setUnitId(request.unitId());
        }

        if (request.name() != null) resident.setName(request.name().trim());
        if (request.email() != null) resident.setEmail(request.email().trim());
        if (request.phone() != null) resident.setPhone(request.phone().trim());
        if (request.cpf() != null) resident.setCpf(normalizeCpf(request.cpf()));
        syncLinkedUser(tenantId, resident, request.hasAccount(), request.accessRole(), request.password());

        resident = residentRepo.save(resident);
        ResidentResponse after = enrichAccount(ResidentResponse.from(resident), tenantId);
        auditService.log("UPDATE", "Resident", resident.getId(), resident.getCondominiumId(), before, after, residentAuditDetails(resident, after));

        return after;
    }

    /**
     * Deleta um morador.
     */
    @Transactional
    public void delete(Long id) {
        String tenantId = TenantContext.get();

        Resident resident = residentRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Morador", "id", id));

        Long effectiveCondoId = UserContext.resolveCondominiumId(resident.getCondominiumId());
        if (effectiveCondoId != null && !effectiveCondoId.equals(resident.getCondominiumId())) {
            throw new ResourceNotFoundException("Morador", "id", id);
        }

        ResidentResponse before = enrichAccount(ResidentResponse.from(resident), tenantId);
        if (resident.getUserId() != null) {
            userRepo.findByTenantIdAndId(tenantId, resident.getUserId()).ifPresent(userRepo::delete);
        }
        residentRepo.delete(resident);
        auditService.log("DELETE", "Resident", id, resident.getCondominiumId(), before, null, residentAuditDetails(resident, before));
    }

    /**
     * Aggregates residents by unit for dashboard counters.
     *
     * @param condominiumIdParam condominiumId vindo do request (usado apenas para SUPERUSER)
     */
    public Map<Long, Long> countByUnit(Long condominiumIdParam) {
        String tenantId = TenantContext.get();
        Long condominiumId = UserContext.resolveCondominiumId(condominiumIdParam);

        if (condominiumId == null) {
            return Map.of();
        }

        Long scopedUnitId = isMorador() ? UserContext.unitId() : null;
        List<Object[]> rows = residentRepo.countByUnit(tenantId, condominiumId, scopedUnitId);
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            Long unitId = (Long) row[0];
            Long count = (Long) row[1];
            result.put(unitId, count);
        }
        return result;
    }

    // ========== Métodos auxiliares ==========

    private void validateCondominiumExists(String tenantId, Long condominiumId) {
        condominiumRepo.findByTenantIdAndId(tenantId, condominiumId)
            .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", condominiumId));
    }

    private void validateUnitExists(String tenantId, Long condominiumId, Long unitId) {
        var unit = unitRepo.findByTenantIdAndId(tenantId, unitId)
            .orElseThrow(() -> new ResourceNotFoundException("Unidade", "id", unitId));

        if (!unit.getCondominiumId().equals(condominiumId)) {
            throw new ResourceNotFoundException("Unidade", "id no condomínio especificado", unitId);
        }
    }

    private void enforceResidentUnitScope(Resident resident) {
        if (!isMorador()) {
            return;
        }
        Long currentUnitId = UserContext.unitId();
        if (currentUnitId == null || !currentUnitId.equals(resident.getUnitId())) {
            throw new ResourceNotFoundException("Morador", "id", resident.getId());
        }
    }

    private boolean isMorador() {
        UserContext.Data ctx = UserContext.get();
        return ctx != null && "MORADOR".equalsIgnoreCase(ctx.role());
    }

    private void maybeCreateLinkedUser(String tenantId, Resident resident, Boolean createAccount, String accessRole, String password) {
        if (!Boolean.TRUE.equals(createAccount)) {
            resident.setUserId(null);
            return;
        }
        if (resident.getEmail() == null || resident.getEmail().isBlank()) {
            throw new BusinessException("E-mail é obrigatório para criar conta de acesso.");
        }
        if (password == null || password.isBlank()) {
            throw new BusinessException("Senha provisória é obrigatória para criar conta de acesso.");
        }
        Role role = parseResidentAccessRole(accessRole);
        ensureUniqueEmail(tenantId, resident.getEmail(), null);

        User user = new User();
        user.setTenantId(tenantId);
        user.setName(resident.getName());
        user.setEmail(resident.getEmail().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setCondominiumId(resident.getCondominiumId());
        user.setUnitId(resident.getUnitId());
        user.setMustChangePassword(true);
        user = userRepo.save(user);
        resident.setUserId(user.getId());
    }

    private void syncLinkedUser(String tenantId, Resident resident, Boolean hasAccount, String accessRole, String password) {
        boolean shouldHaveAccount = hasAccount != null ? hasAccount : resident.getUserId() != null;
        if (!shouldHaveAccount) {
            if (resident.getUserId() != null) {
                deleteLinkedUser(tenantId, resident);
            }
            return;
        }
        if (resident.getEmail() == null || resident.getEmail().isBlank()) {
            throw new BusinessException("E-mail é obrigatório para vincular conta de acesso.");
        }
        if (resident.getUserId() == null) {
            maybeCreateLinkedUser(tenantId, resident, true, accessRole, password);
            return;
        }
        User user = userRepo.findByTenantIdAndId(tenantId, resident.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("Usuário", "id", resident.getUserId()));
        ensureUniqueEmail(tenantId, resident.getEmail(), user.getId());
        user.setName(resident.getName());
        user.setEmail(resident.getEmail().trim().toLowerCase());
        user.setCondominiumId(resident.getCondominiumId());
        user.setUnitId(resident.getUnitId());
        user.setRole(parseResidentAccessRole(accessRole != null ? accessRole : user.getRole().name()));
        if (password != null && !password.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setMustChangePassword(true);
        }
        userRepo.save(user);
    }

    private void deleteLinkedUser(String tenantId, Resident resident) {
        Long currentUserId = UserContext.get() != null ? UserContext.get().userId() : null;
        if (Objects.equals(currentUserId, resident.getUserId())) {
            throw new BusinessException("Você não pode remover a própria conta de acesso pelo cadastro do morador.");
        }
        userRepo.findByTenantIdAndId(tenantId, resident.getUserId()).ifPresent(userRepo::delete);
        resident.setUserId(null);
    }

    private void ensureUniqueEmail(String tenantId, String email, Long ignoreUserId) {
        userRepo.findByTenantIdAndEmailIgnoreCaseExcludingId(tenantId, email, ignoreUserId)
            .ifPresent(existing -> {
                throw new BusinessException("Já existe um usuário com o e-mail informado.");
            });
    }

    private Role parseResidentAccessRole(String accessRole) {
        String value = accessRole == null || accessRole.isBlank() ? "MORADOR" : accessRole.trim().toUpperCase();
        Role role;
        try {
            role = Role.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Role inválida para conta de morador.");
        }
        if (!(role == Role.MORADOR || role == Role.SINDICO || role == Role.ZELADOR)) {
            throw new BusinessException("Conta vinculada a morador só pode usar MORADOR, SINDICO ou ZELADOR.");
        }
        return role;
    }

    private ResidentResponse enrichAccount(ResidentResponse response, String tenantId) {
        if (response.userId() == null) {
            return response;
        }
        String accessRole = userRepo.findByTenantIdAndId(tenantId, response.userId())
            .map(user -> user.getRole().name())
            .orElse(null);
        return ResidentResponse.withAccount(response, accessRole);
    }

    private Map<String, Object> residentAuditDetails(Resident resident, ResidentResponse response) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("residentId", resident.getId());
        details.put("residentName", resident.getName());
        details.put("residentEmail", resident.getEmail());
        details.put("userId", resident.getUserId());
        details.put("unitId", resident.getUnitId());
        details.put("unitLabel", buildUnitLabel(response));
        return details;
    }

    /**
     * Normaliza o CPF removendo máscara e espaços.
     * Retorna null se vazio ou nulo. Armazena somente os 11 dígitos.
     */
    private String normalizeCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return null;
        }
        String digits = cpf.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : digits;
    }

    private String buildUnitLabel(ResidentResponse response) {
        if (response.unitDisplay() != null && !response.unitDisplay().isBlank()) {
            return response.unitDisplay();
        }
        if (response.unitId() == null) {
            return null;
        }
        return unitRepo.findByTenantIdAndId(TenantContext.get(), response.unitId())
            .map(unit -> unit.getBlock() != null && !unit.getBlock().isBlank()
                ? "Unidade " + unit.getNumber() + " - Bloco " + unit.getBlock()
                : "Unidade " + unit.getNumber())
            .orElse("Unidade #" + response.unitId());
    }
}
