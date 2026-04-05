package com.example.condo.web;

import com.example.condo.entity.User;
import com.example.condo.repo.UserRepository;
import com.example.condo.security.Role;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Autenticação / Sessão
 *
 * Endpoints:
 *  - POST /api/auth/login  -> valida credenciais e gera JWT
 *  - GET  /api/auth/me     -> retorna dados do usuário autenticado, lendo o JWT
 *
 * O token carrega: email, tenant, role, expiração.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private static final Logger log = LoggerFactory.getLogger(AuthController.class);

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;

  @Value("${app.jwt.secret:#{null}}")
  private String jwtSecret;

  @Value("${app.jwt.issuer:condo-api}")
  private String issuer;

  @Value("${app.jwt.expirationMinutes:60}")
  private long expirationMinutes;

  public AuthController(
      UserRepository users,
      PasswordEncoder passwordEncoder
  ) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  // -------------------------------------------------
  // GET /api/auth/me
  // Lê o header Authorization: Bearer <token>
  // e responde dados do usuário
  // -------------------------------------------------
  @GetMapping("/me")
  public ResponseEntity<?> me(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestHeader(value = "X-Tenant", required = false) String tenantHeader
  ) {

    // 1. Validar header Authorization
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      log.warn("GET /api/auth/me: missing Authorization header");
      return ResponseEntity.status(401).body(Map.of("error", "missing_token"));
    }

    final String token = authHeader.substring("Bearer ".length()).trim();
    final String tenant = (tenantHeader == null || tenantHeader.isBlank())
        ? "demo"
        : tenantHeader.trim();

    // 2. Resolver a chave JWT
    SecretKey key = resolveKeyOrNull(jwtSecret);
    if (key == null) {
      log.error("GET /api/auth/me: JWT secret not configured");
      return ResponseEntity.status(500).body(Map.of("error", "jwt_secret_not_configured"));
    }

    try {
      // 3. Parsear token e extrair claims
      var claims = Jwts.parserBuilder()
          .setSigningKey(key)
          .setAllowedClockSkewSeconds(60)
          .build()
          .parseClaimsJws(token)
          .getBody();

      String email = claims.getSubject();
      String roleStr = claims.get("role", String.class);
      String tokenTenant = claims.get("tenant", String.class);

      if (email == null || email.isBlank()) {
        log.warn("GET /api/auth/me: token without valid subject");
        return ResponseEntity.status(401).body(Map.of("error", "invalid_token"));
      }

      // 4. Preparar objeto de resposta
      Map<String, Object> userData = new HashMap<>();
      userData.put("email", email);
      userData.put(
          "tenant",
          (tokenTenant != null && !tokenTenant.isBlank()) ? tokenTenant : tenant
      );

      // 5. Buscar usuário no banco para enriquecer com name, role enum e unitId
      Optional<User> userOpt = users.findByTenantAndEmail(tenant, email);

      if (userOpt.isPresent()) {
        User user = userOpt.get();

        userData.put("id", user.getId());
        userData.put("name", user.getName());

        Role enumRole = user.getRole();
        userData.put("role", enumRole != null ? enumRole.name() : roleStr);

        // unitId pode ainda não existir na entidade User.
        try {
          Object unitId = user.getClass()
              .getMethod("getUnitId")
              .invoke(user);
          userData.put("unitId", unitId);
        } catch (Exception ignore) {
          // se não tiver getUnitId(), seguimos sem isso
        }

      } else {
        // fallback
        userData.put("name", "Usuário");
        userData.put("role", (roleStr != null && !roleStr.isBlank()) ? roleStr : "RESIDENT");
      }

      log.info("GET /api/auth/me: success for user {}", email);
      return ResponseEntity.ok(userData);

    } catch (Exception e) {
      log.warn("GET /api/auth/me: invalid token - {}", e.getMessage());
      return ResponseEntity.status(401).body(Map.of("error", "invalid_token"));
    }
  }

  // -------------------------------------------------
  // POST /api/auth/login
  // Body JSON:
  // {
  //   "email": "morador@demo.com",
  //   "password": "123456"
  // }
  //
  // Header opcional:
  //   X-Tenant: demo
  //
  // Resposta:
  // {
  //   "token": "...",
  //   "accessToken": "...",
  //   "tokenType": "Bearer",
  //   "role": "RESIDENT",
  //   "roles": ["RESIDENT"],
  //   "tenant": "demo",
  //   "expiresAt": 1730332400,
  //   "user": {...}
  // }
  // -------------------------------------------------
  @PostMapping("/login")
  public ResponseEntity<?> login(
      @RequestBody LoginReq body,
      @RequestHeader(value = "X-Tenant", required = false) String tenantHeader
  ) {

    final String email = body.email().trim().toLowerCase();
    final String password = body.password().trim();
    final String tenant = (tenantHeader == null || tenantHeader.isBlank())
        ? "demo"
        : tenantHeader.trim();

    log.info("POST /api/auth/login: attempt for email={}, tenant={}", email, tenant);

    // Validações básicas
    if (email.isBlank() || password.isBlank()) {
      log.warn("POST /api/auth/login: empty credentials");
      return ResponseEntity.status(400).body(Map.of("error", "email_and_password_required"));
    }

    boolean authenticated = false;
    String roleAsString = "RESIDENT";
    User authenticatedUser = null;

    // Tenta autenticar com usuário do banco
    // IMPORTANTE: Usa findByTenantAndEmail que já existe no seu UserRepository
    Optional<User> userOpt = users.findByTenantAndEmail(tenant, email);
    
    if (userOpt.isPresent()) {
      User user = userOpt.get();
      String storedHash = user.getPasswordHash();
      
      log.debug("POST /api/auth/login: found user id={}, name={}, role={}", 
          user.getId(), user.getName(), user.getRole());
      
      if (storedHash != null && !storedHash.isBlank()) {
        // Valida com BCrypt
        try {
          authenticated = passwordEncoder.matches(password, storedHash);
          log.debug("POST /api/auth/login: BCrypt match result={} for user={}", 
              authenticated, email);
        } catch (Exception e) {
          log.error("POST /api/auth/login: error matching password - {}", e.getMessage(), e);
        }
        
        if (authenticated) {
          authenticatedUser = user;
          Role role = user.getRole();
          roleAsString = (role != null ? role.name() : "RESIDENT");
          log.info("POST /api/auth/login: ✅ authentication successful for user={}, role={}", 
              email, roleAsString);
        } else {
          log.warn("POST /api/auth/login: ❌ password mismatch for user={}", email);
        }
      } else {
        log.warn("POST /api/auth/login: user {} has null/empty password_hash", email);
      }
    } else {
      log.warn("POST /api/auth/login: ❌ user not found - email={}, tenant={}", email, tenant);
    }

    if (!authenticated) {
      log.warn("POST /api/auth/login: AUTHENTICATION FAILED for {}", email);
      return ResponseEntity.status(401).body(Map.of(
          "error", "invalid_credentials",
          "message", "Email ou senha inválidos"
      ));
    }

    // Array de roles pro front (RoleGuard usa isso)
    String[] roles = new String[]{ roleAsString };

    // Gerar JWT
    SecretKey key = resolveKeyOrNull(jwtSecret);
    if (key == null) {
      log.error("POST /api/auth/login: JWT secret not configured");
      return ResponseEntity.status(500).body(Map.of("error", "jwt_secret_not_configured"));
    }

    Instant now = Instant.now();
    Instant exp = now.plusSeconds(expirationMinutes * 60L);

    String tokenStr = Jwts.builder()
        .setSubject(email)
        .claim("tenant", tenant)
        .claim("role", roleAsString)
        .claim("roles", roles)
        .setIssuer(issuer)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(exp))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();

    Map<String, Object> response = new HashMap<>();
    response.put("token", tokenStr);
    response.put("accessToken", tokenStr);
    response.put("tokenType", "Bearer");
    response.put("role", roleAsString);
    response.put("roles", roles);
    response.put("tenant", tenant);
    response.put("expiresAt", exp.getEpochSecond());
    
    // Adiciona informações do usuário na resposta
    if (authenticatedUser != null) {
      Map<String, Object> userInfo = new HashMap<>();
      userInfo.put("id", authenticatedUser.getId());
      userInfo.put("name", authenticatedUser.getName());
      userInfo.put("email", authenticatedUser.getEmail());
      userInfo.put("role", roleAsString);
      response.put("user", userInfo);
    }

    log.info("POST /api/auth/login: ✅ token generated successfully for user={}, expires at {}", 
        email, exp);
    return ResponseEntity.ok(response);
  }

  // -------------------------------------------------
  // Helpers internos
  // -------------------------------------------------

  /**
   * Monta a chave HMAC SHA-256 a partir da string secreta
   * Suporta formato base64:xxxxx ou texto direto
   */
  private SecretKey resolveKeyOrNull(String secret) {
    if (secret == null || secret.isBlank()) {
      log.error("JWT secret is null or blank");
      return null;
    }
    
    try {
      // Suporta formato base64:xxxxx
      if (secret.startsWith("base64:")) {
        byte[] decoded = Base64.getDecoder().decode(secret.substring("base64:".length()));
        return new SecretKeySpec(decoded, "HmacSHA256");
      }
      
      // Formato direto
      byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
      return new SecretKeySpec(keyBytes, "HmacSHA256");
    } catch (Exception e) {
      log.error("Error resolving JWT key: {}", e.getMessage());
      return null;
    }
  }

  /**
   * DTO do corpo de login
   */
  public record LoginReq(
      String email,
      String password
  ) {}
}