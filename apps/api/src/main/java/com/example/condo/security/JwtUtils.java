package com.example.condo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Utilitário para gerar e validar JWTs.
 * - Aceita segredo em texto ou em Base64 com prefixo "base64:".
 * - Gera tokens com o claim "roles" (lista) e, por compatibilidade, também "role" (string).
 *   O JwtAuthFilter aceita qualquer um dos dois.
 */
public class JwtUtils {

  private static byte[] resolveSecretBytes(String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("JWT secret (app.jwt.secret) não configurado.");
    }
    if (secret.startsWith("base64:")) {
      return Decoders.BASE64.decode(secret.substring(7));
    }
    // Se usar string “normal”, garanta >= 32 chars para HS256
    return secret.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Assinatura original (compatibilidade): um único papel.
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
   * Nova assinatura: múltiplos papéis.
   * Grava "roles" (lista) e também "role" (primeiro da lista) por compatibilidade.
   */
  public static String createToken(String subject,
                                   List<String> roles,
                                   String issuer,
                                   String secret,
                                   int expirationMinutes) {
    byte[] keyBytes = resolveSecretBytes(secret); // >= 32 bytes
    Key key = Keys.hmacShaKeyFor(keyBytes);
    Instant now = Instant.now();

    // papel principal (usado em "role" por compatibilidade)
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