package com.example.condo.service;

import com.example.condo.dto.auth.LoginRequest;
import com.example.condo.dto.auth.LoginResponse;
import com.example.condo.dto.auth.UserMeResponse;
import com.example.condo.entity.User;
import com.example.condo.exception.UnauthorizedException;
import com.example.condo.repo.UserRepository;
import com.example.condo.security.JwtUtils;
import com.example.condo.tenant.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service para autenticação e autorização.
 *
 * Responsabilidades:
 * - Login com email/senha
 * - Geração de tokens JWT
 * - Validação de credenciais
 * - Refresh de tokens (futuro)
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public AuthService(
        UserRepository userRepo,
        PasswordEncoder passwordEncoder,
        JwtUtils jwtUtils
    ) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    /**
     * Realiza login e retorna token JWT.
     *
     * @throws UnauthorizedException se credenciais inválidas ou tenant não fornecido
     */
    public LoginResponse login(LoginRequest request) {
        String tenantId = TenantContext.get();

        if (tenantId == null || tenantId.isBlank()) {
            throw new UnauthorizedException("Tenant não foi fornecido no header (X-Tenant ou X-Tenant-ID)");
        }

        // Busca usuário
        User user = userRepo.findByTenantAndEmail(tenantId, request.email())
            .orElseThrow(() -> new UnauthorizedException("Credenciais inválidas"));

        // Valida senha
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Credenciais inválidas");
        }

        // Gera token JWT com claims de isolamento de tenant
        String token = jwtUtils.generate(
            user.getEmail(),
            tenantId,
            user.getRole().name(),
            user.getCondominiumId(),
            user.getUnitId(),
            user.getId()
        );

        return LoginResponse.of(
            token,
            user.getEmail(),
            user.getName(),
            user.getRole(),
            tenantId,
            user.getCondominiumId(),
            user.getUnitId(),
            user.getId()
        );
    }

    /**
     * Busca dados do usuário autenticado pelo email.
     */
    public UserMeResponse getAuthenticatedUser(String email) {
        String tenantId = TenantContext.get();

        User user = userRepo.findByTenantAndEmail(tenantId, email)
            .orElseThrow(() -> new UnauthorizedException("Usuário não encontrado ou tenant inválido"));

        return UserMeResponse.from(user);
    }

    /**
     * Autentica usuário (método legado - mantido para compatibilidade).
     *
     * @return User se autenticado, null se falhar
     * @deprecated Usar login(LoginRequest) que lança exceção em caso de erro
     */
    @Deprecated
    public User authenticate(String email, String rawPassword) {
        String tenantId = TenantContext.get();

        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }

        return userRepo.findByTenantAndEmail(tenantId, email)
            .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
            .orElse(null);
    }
}
