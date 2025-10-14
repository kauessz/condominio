package com.example.condo.web;

import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@RestController
@RequestMapping({"/auth", "/api/auth"})
public class AuthController {

  @Value("${app.jwt.secret:}")
  private String jwtSecret;

  @Value("${app.jwt.issuer:condo}")
  private String issuer;

  @Value("${app.jwt.expirationMinutes:120}")
  private long expirationMinutes;

  private SecretKey resolveKeyOrNull(String secret) {
    if (secret == null || secret.isBlank()) return null;
    if (secret.startsWith("base64:")) {
      byte[] decoded = Base64.getDecoder().decode(secret.substring("base64:".length()));
      return new SecretKeySpec(decoded, "HmacSHA256");
    }
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  public record LoginReq(@NotBlank String email, @NotBlank String password) {}

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginReq body,
                                 @RequestHeader(value = "X-Tenant", required = false) String tenantHeader) {

    // EXEMPLO simples: autenticação em memória para admin demo
    String email = body.email().trim().toLowerCase();
    String password = body.password().trim();

    if (!email.equals("admin@demo.com") || !password.equals("admin123")) {
      return ResponseEntity.status(401).body(Map.of("error", "invalid_credentials"));
    }

    String tenant = (tenantHeader == null || tenantHeader.isBlank()) ? "demo" : tenantHeader.trim();
    String role = "ADMIN";

    SecretKey key = resolveKeyOrNull(jwtSecret);
    if (key == null) {
      return ResponseEntity.status(500).body(Map.of("error", "jwt_secret_not_configured"));
    }

    long nowSec = System.currentTimeMillis() / 1000L;
    long expSec = nowSec + (expirationMinutes * 60);

    String token = Jwts.builder()
        .setSubject(email)
        .claim("role", role)
        .setIssuer(issuer)
        .setIssuedAt(new java.util.Date(nowSec * 1000L))
        .setExpiration(new java.util.Date(expSec * 1000L))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();

    // Retorna token (e accessToken por compat), mais infos úteis
    return ResponseEntity.ok(Map.of(
        "token", token,
        "accessToken", token,   // alias
        "tenant", tenant,
        "role", role
    ));
  }
}