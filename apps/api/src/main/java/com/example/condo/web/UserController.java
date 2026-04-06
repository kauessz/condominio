package com.example.condo.web;

import com.example.condo.dto.common.PageResponse;
import com.example.condo.dto.assembly.AssemblyElectionCandidateResponse;
import com.example.condo.dto.user.CreateUserRequest;
import com.example.condo.dto.user.UpdateUserRequest;
import com.example.condo.dto.user.UserResponse;
import com.example.condo.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller de gerenciamento de usuários do sistema.
 *
 * Endpoints:
 * - GET    /api/users           → Lista usuários (SUPERUSER vê todos; demais, apenas do próprio condo)
 * - GET    /api/users/{id}      → Detalhe (mesma regra de visibilidade)
 * - POST   /api/users           → Cria usuário (SUPERUSER, ADMIN, SINDICO)
 * - PUT    /api/users/{id}      → Atualiza role/condomínio (SUPERUSER apenas)
 * - DELETE /api/users/{id}      → Remove usuário (SUPERUSER apenas)
 */
@RestController
@RequestMapping({"/users", "/api/users"})
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /api/users
     *
     * Lista usuários com paginação e busca por nome/e-mail.
     * SUPERUSER → todos no tenant. Demais → apenas do próprio condomínio.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public ResponseEntity<PageResponse<UserResponse>> list(
        @RequestParam(value = "q", required = false) String query,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ResponseEntity.ok(userService.list(query, page, pageSize));
    }

    /**
     * GET /api/users/{id}
     *
     * Detalhe de um usuário específico.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @GetMapping("/election-candidates")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public ResponseEntity<java.util.List<AssemblyElectionCandidateResponse>> listElectionCandidates(
        @RequestParam(value = "condominiumId", required = false) Long condominiumId
    ) {
        return ResponseEntity.ok(userService.listElectionCandidates(condominiumId));
    }

    /**
     * POST /api/users
     *
     * Cria um novo usuário.
     *
     * TAREFA 4 — condominiumId obrigatório para não-SUPERUSER:
     * - Se o usuário criador não for SUPERUSER, o condominiumId vem do JWT.
     * - Se o JWT não tiver condominiumId → 422 BusinessException.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public ResponseEntity<UserResponse> create(
        @Valid @RequestBody CreateUserRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    /**
     * PUT /api/users/{id}
     *
     * Atualiza role e/ou condomínio de um usuário.
     * Permitido para SUPERUSER e ADMIN dentro do escopo permitido.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN')")
    public ResponseEntity<UserResponse> update(
        @PathVariable Long id,
        @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    /**
     * DELETE /api/users/{id}
     *
     * Remove um usuário do sistema.
     * Permitido para SUPERUSER e ADMIN dentro do escopo permitido.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
