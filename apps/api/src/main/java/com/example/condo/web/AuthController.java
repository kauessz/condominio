package com.example.condo.web;

import com.example.condo.dto.auth.LoginRequest;
import com.example.condo.dto.auth.LoginResponse;
import com.example.condo.dto.auth.UserMeResponse;
import com.example.condo.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Controller de autenticação.
 *
 * Endpoints:
 * - POST /api/auth/login -> Autentica usuário e retorna JWT
 * - GET  /api/auth/me    -> Retorna dados do usuário autenticado
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/login
     *
     * Autentica usuário com email e senha, retorna token JWT.
     *
     * Headers necessários:
     * - X-Tenant ou X-Tenant-ID: identificador do condomínio
     *
     * Body:
     * {
     *   "email": "usuario@exemplo.com",
     *   "password": "senha123"
     * }
     *
     * Resposta (200):
     * {
     *   "token": "eyJhbGc...",
     *   "type": "Bearer",
     *   "email": "usuario@exemplo.com",
     *   "name": "Nome do Usuário",
     *   "role": "ADMIN",
     *   "tenant": "demo"
     * }
     *
     * Erros:
     * - 401: Credenciais inválidas ou tenant não fornecido
     * - 400: Campos de validação inválidos
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/auth/login: attempt for email={}", request.email());

        LoginResponse response = authService.login(request);

        log.info("POST /api/auth/login: ✅ success for user={}, role={}",
            response.email(), response.role());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/auth/me
     *
     * Retorna dados do usuário autenticado.
     *
     * Headers necessários:
     * - Authorization: Bearer <token>
     * - X-Tenant ou X-Tenant-ID: identificador do condomínio
     *
     * Resposta (200):
     * {
     *   "id": 1,
     *   "email": "usuario@exemplo.com",
     *   "name": "Nome do Usuário",
     *   "role": "ADMIN",
     *   "tenant": "demo",
     *   "createdAt": "2024-01-15T10:30:00Z"
     * }
     *
     * Erros:
     * - 401: Token inválido ou expirado
     * - 403: Tenant inválido
     */
    @GetMapping("/me")
    public ResponseEntity<UserMeResponse> me(Authentication authentication) {
        // O email vem do JWT que foi parseado pelo JwtAuthFilter
        String email = authentication.getName();

        log.debug("GET /api/auth/me: request for user={}", email);

        UserMeResponse response = authService.getAuthenticatedUser(email);

        return ResponseEntity.ok(response);
    }
}
