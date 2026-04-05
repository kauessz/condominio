package com.example.condo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Utilitário para gerar e validar JWTs.
 *
 * Claims emitidos:
 *   - sub          → email do usuário
 *   - tenant       → tenantId
 *   - role         → role principal (string)
 *   - roles        → lista de roles (compatibilidade)
 *   - condominiumId → ID do condomínio (null para SUPERUSER)
 *   - unitId       → ID da unidade (não-null apenas para MORADOR)
 *   - userId       → ID numérico do usuário no banco
 *
 * Aceita segredo em texto ou em Base64 com prefixo "base64:".
 */
@Component
public class JwtUtils {

  private final String secret;
  private final String issuer;
  private final int expirationMinutes;

  public JwtUtils(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.issuer:condo}") String issuer,
      @Value("${app.jwt.expirationMinutes:120}") int expirationMinutes
  ) {
    this.secret = secret;
    this.issuer = issuer;
    this.expirationMinutes = expirationMinutes;
  }

  private static byte[] resolveSecretBytes(String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("JWT secret (app.jwt.secret) não configurado.");
    }
    if (secret.startsWith("base64:")) {
      return Decoders.BASE64.decode(secret.substring(7));
    }
    return secret.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Gera token JWT com claims de isolamento de tenant.
   *
   * @param email          e-mail / subject do token
   * @param tenantId       tenant (string)
   * @param role           role principal do usuário
   * @param condominiumId  ID do condomínio (null para SUPERUSER)
   * @param unitId         ID da unidade (null se não for MORADOR)
   * @param userId         ID numérico do usuário no banco
   */
  public String generate(String email, String tenantId, String role,
                         Long condominiumId, Long unitId, Long userId) {
    byte[] keyBytes = resolveSecretBytes(secret);
    Key key = Keys.hmacShaKeyFor(keyBytes);
    Instant now = Instant.now();

    return Jwts.builder()
        .setSubject(email)
        .claim("tenant", tenantId)
        .claim("role", role)
        .claim("roles", List.of(role))
        .claim("condominiumId", condominiumId)
        .claim("unitId", unitId)
        .claim("userId", userId)
        .setIssuer(issuer)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  /**
   * Sobrecarga legada sem condominiumId/unitId/userId (compatibilidade).
   * @deprecated Use generate(email, tenantId, role, condominiumId, unitId, userId)
   */
  @Deprecated
  public String generate(String email, String tenantId, String role) {
    return generate(email, tenantId, role, null, null, null);
  }

  /**
   * Assinatura estática original (compatibilidade): um único papel.
   */
  public static String createToken(String subject,
                                   String role,
                                   String issuer,
                                   String secret,
                                   int expirationMinutes) {
    return createToken(subject,
        role == null ? List.of("USER") : List.of(role),
        issuer, secret, expirationMinutes);
  }

  /**
   * Nova assinatura estática: múltiplos papéis.
   */
  public static String createToken(String subject,
                                   List<String> roles,
                                   String issuer,
                                   String secret,
                                   int expirationMinutes) {
    byte[] keyBytes = resolveSecretBytes(secret);
    Key key = Keys.hmacShaKeyFor(keyBytes);
    Instant now = Instant.now();

    String primaryRole = roles == null || roles.isEmpty()
        ? "USER"
        : roles.stream().filter(Objects::nonNull).findFirst().orElse("USER");

    return Jwts.builder()
        .setSubject(subject)
        .claim("roles", roles == null || roles.isEmpty() ? List.of("USER") : roles)
        .claim("role", primaryRole)
        .setIssuer(issuer)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  public static Claims parse(String token, String secret) {
    byte[] keyBytes = resolveSecretBytes(secret);
    return Jwts.parserBuilder()
        .setSigningKey(Keys.hmacShaKeyFor(keyBytes))
        .build()
        .parseClaimsJws(token)
        .getBody();
  }
}
