package com.example.condo.service;

import com.example.condo.dto.common.PageResponse;
import com.example.condo.dto.user.CreateUserRequest;
import com.example.condo.dto.user.UpdateUserRequest;
import com.example.condo.dto.user.UserResponse;
import com.example.condo.entity.User;
import com.example.condo.exception.BusinessException;
import com.example.condo.exception.ResourceNotFoundException;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.repo.UserRepository;
import com.example.condo.security.Role;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Service para gerenciamento de usuários do sistema.
 *
 * Isolamento de tenant:
 * - SUPERUSER: vê e gerencia todos os usuários do tenant
 * - Demais roles: veem apenas usuários do próprio condomínio
 *
 * Regra de condominiumId na criação (TAREFA 4):
 * - Não-SUPERUSER criando usuário: condominiumId vem do JWT (não do request)
 * - Se o JWT não tiver condominiumId → 422
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepo;
    private final CondominiumRepository condominiumRepo;
    private final UnitRepository unitRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(
        UserRepository userRepo,
        CondominiumRepository condominiumRepo,
        UnitRepository unitRepo,
        PasswordEncoder passwordEncoder,
        AuditService auditService
    ) {
        this.userRepo = userRepo;
        this.condominiumRepo = condominiumRepo;
        this.unitRepo = unitRepo;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * Lista usuários com paginação e busca.
     *
     * SUPERUSER → todos os usuários do tenant.
     * Demais roles → apenas usuários do próprio condomínio.
     */
    public PageResponse<UserResponse> list(String query, int page, int pageSize) {
        String tenantId = TenantContext.get();
        Pageable pageable = PageRequest.of(sanitizePage(page), sanitizePageSize(pageSize));
        String normalizedQuery = blankToNull(query);
        UserContext.Data ctx = UserContext.get();

        log.info(
            "Listando usuários tenant={} role={} condominiumId={} page={} size={} q={}",
            tenantId,
            ctx != null ? ctx.role() : null,
            ctx != null ? ctx.condominiumId() : null,
            pageable.getPageNumber(),
            pageable.getPageSize(),
            normalizedQuery
        );

        Page<User> result = switch (resolveListScope(ctx)) {
            case SUPERUSER -> listForSuperuser(tenantId, normalizedQuery, pageable);
            case ADMIN -> listByCondominium(tenantId, ctx, normalizedQuery, pageable);
            case SINDICO -> listResidentsByCondominium(tenantId, ctx, normalizedQuery, pageable);
        };

        List<UserResponse> items = result.getContent().stream()
            .map(UserResponse::from)
            .toList();

        return PageResponse.of(items, pageable.getPageNumber(), pageable.getPageSize(), result.getTotalElements());
    }

    /**
     * Busca usuário por ID.
     *
     * Não-SUPERUSER só pode acessar usuários do próprio condomínio.
     */
    public UserResponse getById(Long id) {
        String tenantId = TenantContext.get();
        User user = findOrThrow(tenantId, id);
        assertAccessToUser(user);
        return UserResponse.from(user);
    }

    /**
     * Cria um novo usuário no sistema.
     *
     * TAREFA 4 — validação de condominiumId:
     * - Não-SUPERUSER: condominiumId vem SEMPRE do JWT.
     *   Se o JWT não tiver condominiumId → 422.
     * - SUPERUSER: condominiumId pode vir do request.
     *   Para roles que exigem condomínio (não-SUPERUSER), deve ser informado.
     */
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String tenantId = TenantContext.get();

        // --- Validar role ---
        Role newRole = parseRole(request.role());
        validateCreatorScope(newRole);

        // --- Resolver condominiumId com isolamento de tenant ---
        Long condominiumId = UserContext.resolveCondominiumId(request.condominiumId());

        // Para roles que precisam de condomínio (qualquer role != SUPERUSER),
        // o condominiumId é obrigatório.
        if (newRole != Role.SUPERUSER && condominiumId == null) {
            throw new BusinessException(
                "condominiumId é obrigatório para a role " + newRole.name() +
                ". Associe o usuário a um condomínio."
            );
        }

        // --- Validar unicidade de e-mail ---
        if (userRepo.existsByTenantIdAndEmail(tenantId, request.email())) {
            throw new BusinessException("Já existe um usuário com o e-mail: " + request.email());
        }

        // --- Validar condomínio ---
        if (condominiumId != null) {
            condominiumRepo.findByTenantIdAndId(tenantId, condominiumId)
                .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", condominiumId));
        }

        // --- Montar entidade ---
        User user = new User();
        user.setTenantId(tenantId);
        user.setName(request.name().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(newRole);
        user.setCondominiumId(condominiumId);
        user.setUnitId(request.unitId());
        validateRoleScope(tenantId, user);

        user = userRepo.save(user);
        auditService.log("CREATE", "User", user.getId(), user.getCondominiumId(), null, UserResponse.from(user));
        return UserResponse.from(user);
    }

    /**
     * Atualiza role e/ou condomínio de um usuário.
     *
     * Uso exclusivo de SUPERUSER — garantido no Controller via @PreAuthorize.
     */
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        String tenantId = TenantContext.get();
        User user = findOrThrow(tenantId, id);
        UserResponse before = UserResponse.from(user);

        if (request.role() != null) {
            Role newRole = parseRole(request.role());

            // Não deixar remover SUPERUSER de si mesmo
            if (user.getRole() == Role.SUPERUSER && newRole != Role.SUPERUSER) {
                Long myId = UserContext.get() != null ? UserContext.get().userId() : null;
                if (user.getId().equals(myId)) {
                    throw new BusinessException("Você não pode rebaixar a própria conta de SUPERUSER.");
                }
            }

            user.setRole(newRole);
        }

        // condominiumId: valor 0 = remover vínculo (SUPERUSER sem condo)
        if (request.condominiumId() != null) {
            Long newCondoId = request.condominiumId() == 0L ? null : request.condominiumId();
            if (newCondoId != null) {
                condominiumRepo.findByTenantIdAndId(tenantId, newCondoId)
                    .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", newCondoId));
            }
            user.setCondominiumId(newCondoId);
        }

        if (request.unitId() != null) {
            user.setUnitId(request.unitId() == 0L ? null : request.unitId());
        }

        validateRoleScope(tenantId, user);

        user = userRepo.save(user);
        auditService.log("UPDATE", "User", user.getId(), user.getCondominiumId(), before, UserResponse.from(user));
        return UserResponse.from(user);
    }

    /**
     * Remove um usuário do sistema.
     * Uso exclusivo de SUPERUSER — garantido no Controller.
     */
    @Transactional
    public void delete(Long id) {
        String tenantId = TenantContext.get();
        User user = findOrThrow(tenantId, id);

        Long myId = UserContext.get() != null ? UserContext.get().userId() : null;
        if (user.getId().equals(myId)) {
            throw new BusinessException("Você não pode excluir a própria conta.");
        }

        UserResponse before = UserResponse.from(user);
        userRepo.delete(user);
        auditService.log("DELETE", "User", id, user.getCondominiumId(), before, null);
    }

    // ======================================================================
    // Helpers
    // ======================================================================

    private User findOrThrow(String tenantId, Long id) {
        return userRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário", "id", id));
    }

    /**
     * Valida que não-SUPERUSER acessa apenas usuários do próprio condomínio.
     */
    private void assertAccessToUser(User target) {
        if (UserContext.isSuperuser()) return;

        UserContext.Data ctx = UserContext.get();
        Long myCondoId = ctx != null ? ctx.condominiumId() : null;

        if (myCondoId == null || !myCondoId.equals(target.getCondominiumId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado.");
        }

        if ("SINDICO".equalsIgnoreCase(ctx != null ? ctx.role() : null) && target.getRole() != Role.MORADOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Síndico pode acessar apenas moradores.");
        }
    }

    private Role parseRole(String roleName) {
        try {
            return Role.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                "Role inválida: '" + roleName + "'. Roles válidas: " +
                Arrays.stream(Role.values()).map(Enum::name).reduce((a, b) -> a + ", " + b).orElse("")
            );
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private void validateRoleScope(String tenantId, User user) {
        Role role = user.getRole();
        Long condominiumId = user.getCondominiumId();
        Long unitId = user.getUnitId();

        if (role == Role.SUPERUSER) {
            user.setCondominiumId(null);
            user.setUnitId(null);
            return;
        }

        if (condominiumId == null) {
            throw new BusinessException("condominiumId é obrigatório para a role " + role.name());
        }

        if (requiresUnit(role) && unitId == null) {
            throw new BusinessException("unitId é obrigatório para a role " + role.name());
        }

        if (!requiresUnit(role)) {
            user.setUnitId(null);
            return;
        }

        if (!unitRepo.existsByTenantIdAndIdAndCondominiumId(tenantId, unitId, condominiumId)) {
            throw new BusinessException("A unidade informada não pertence ao condomínio selecionado");
        }
    }

    private void validateCreatorScope(Role newRole) {
        if (UserContext.isSuperuser()) {
            return;
        }
        UserContext.Data ctx = UserContext.get();
        String currentRole = ctx != null ? ctx.role() : null;
        if (currentRole == null) {
            throw new BusinessException("Contexto do usuário autenticado não disponível.");
        }
        if (newRole == Role.SUPERUSER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas SUPERUSER pode criar outro SUPERUSER.");
        }
        if ("SINDICO".equalsIgnoreCase(currentRole) && newRole == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Síndico não pode criar Administrador.");
        }
    }

    private boolean requiresUnit(Role role) {
        return role == Role.MORADOR || role == Role.SINDICO || role == Role.ZELADOR;
    }

    private Page<User> listByCondominium(String tenantId, UserContext.Data ctx, String query, Pageable pageable) {
        Long condominiumId = requireCondominiumId(ctx);
        if (query == null) {
            return userRepo.findByTenantAndCondominium(tenantId, condominiumId, pageable);
        }
        return userRepo.findByTenantAndCondominium(tenantId, condominiumId, query, pageable);
    }

    private Page<User> listResidentsByCondominium(String tenantId, UserContext.Data ctx, String query, Pageable pageable) {
        Long condominiumId = requireCondominiumId(ctx);
        if (query == null) {
            return userRepo.findByTenantAndCondominiumAndRoles(
                tenantId,
                condominiumId,
                Set.of(Role.MORADOR),
                pageable
            );
        }
        return userRepo.findByTenantAndCondominiumAndRoles(
            tenantId,
            condominiumId,
            Set.of(Role.MORADOR),
            query,
            pageable
        );
    }

    private Long requireCondominiumId(UserContext.Data ctx) {
        Long condominiumId = ctx != null ? ctx.condominiumId() : null;
        if (condominiumId == null) {
            throw new BusinessException("Usuário autenticado sem condomínio associado.");
        }
        return condominiumId;
    }

    private ListScope resolveListScope(UserContext.Data ctx) {
        if (ctx == null || ctx.role() == null) {
            throw new BusinessException("Contexto do usuário autenticado não disponível.");
        }

        return switch (ctx.role().toUpperCase()) {
            case "SUPERUSER" -> ListScope.SUPERUSER;
            case "ADMIN" -> ListScope.ADMIN;
            case "SINDICO" -> ListScope.SINDICO;
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Perfil sem acesso à listagem de usuários.");
        };
    }

    private Page<User> listForSuperuser(String tenantId, String query, Pageable pageable) {
        if (query == null) {
            return userRepo.findAllByTenant(tenantId, pageable);
        }
        return userRepo.findAllByTenant(tenantId, query, pageable);
    }

    private int sanitizePage(int page) {
        return Math.max(page, 0);
    }

    private int sanitizePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private enum ListScope {
        SUPERUSER,
        ADMIN,
        SINDICO
    }
}
