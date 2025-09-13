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

  public static String createToken(String subject, String role, String issuer,
                                   String secret, int expirationMinutes) {
    byte[] keyBytes = resolveSecretBytes(secret);     // >= 32 bytes
    Key key = Keys.hmacShaKeyFor(keyBytes);
    Instant now = Instant.now();
    return Jwts.builder()
        .setSubject(subject)
        .claim("role", role)
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