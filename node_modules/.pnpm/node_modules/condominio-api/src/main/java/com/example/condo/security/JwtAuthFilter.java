package com.example.condo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

  @Value("${app.jwt.secret:}")
  private String jwtSecret;

  @Value("${app.jwt.issuer:condo}")
  private String issuer;

  // Se true, um issuer diferente causa 401; se false, apenas loga e segue
  @Value("${app.jwt.strict-issuer:false}")
  private boolean strictIssuer;

  private SecretKey resolveKey(String secret) {
    if (secret == null || secret.isBlank()) return null;
    if (secret.startsWith("base64:")) {
      byte[] decoded = Base64.getDecoder().decode(secret.substring("base64:".length()));
      return new SecretKeySpec(decoded, "HmacSHA256");
    }
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain
  ) throws ServletException, IOException {

    final String path = request.getRequestURI();

    // Endpoints públicos de auth
    if (path.startsWith("/api/auth/") || path.startsWith("/auth/")) {
      filterChain.doFilter(request, response);
      return;
    }

    String auth = request.getHeader("Authorization");
    if (auth == null || !auth.startsWith("Bearer ")) {
      // Sem token -> segue; a SecurityChain responderá 401 em rotas protegidas
      filterChain.doFilter(request, response);
      return;
    }

    String token = auth.substring("Bearer ".length()).trim();

    SecretKey key = resolveKey(jwtSecret);
    if (key == null) {
      log.warn("JWT: jwtSecret vazio/não configurado");
      write401(response, "jwt_secret_not_configured");
      return;
    }

    try {
      // Parse e validação do token
      Jws<Claims> jws = Jwts.parserBuilder()
          .setSigningKey(key)
          .setAllowedClockSkewSeconds(60)
          .build()
          .parseClaimsJws(token);

      Claims claims = jws.getBody();

      // Checagem de issuer (opcionalmente estrita)
      String iss = claims.getIssuer();
      if (issuer != null && !issuer.isBlank() && !Objects.equals(issuer, iss)) {
        log.warn("JWT: issuer inválido. Esperado='{}' Recebido='{}' (strict={})", issuer, iss, strictIssuer);
        if (strictIssuer) {
          write401(response, "bad_issuer");
          return;
        }
      }

      String subject = claims.getSubject(); // ex: email
      if (subject == null || subject.isBlank()) {
        log.warn("JWT: subject ausente");
        write401(response, "no_subject");
        return;
      }

      // Aceita "role": "ADMIN" OU "roles": ["ADMIN","USER"] ou "roles": "ADMIN"
      List<SimpleGrantedAuthority> authorities = extractAuthorities(claims);
      if (authorities.isEmpty()) {
        // Apenas autenticado para GETs já basta; mas logamos para clareza
        log.debug("JWT: sem roles mapeadas; prosseguindo apenas autenticado");
      }

      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(subject, null, authorities);

      SecurityContextHolder.getContext().setAuthentication(authentication);

    } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
      // Token inválido/expirado -> 401
      log.warn("JWT inválido: {}", e.toString());
      write401(response, "unauthorized");
      return;
    }

    // Importante: deixe o resto da cadeia executar.
    // Se o controller/repository lançar erro, ele NÃO vira 401 aqui.
    filterChain.doFilter(request, response);
  }

  private List<SimpleGrantedAuthority> extractAuthorities(Claims claims) {
    Object rolesClaim = claims.get("roles");
    if (rolesClaim == null) rolesClaim = claims.get("role");

    List<String> roleNames = new ArrayList<>();

    if (rolesClaim instanceof Collection<?> col) {
      for (Object o : col) {
        if (o != null) roleNames.add(o.toString());
      }
    } else if (rolesClaim != null) {
      roleNames.add(rolesClaim.toString());
    }

    if (roleNames.isEmpty()) return List.of();

    List<SimpleGrantedAuthority> authorities = new ArrayList<>(roleNames.size());
    for (String r : roleNames) {
      if (r == null || r.isBlank()) continue;
      String rn = r.toUpperCase(Locale.ROOT).strip();
      if (!rn.startsWith("ROLE_")) rn = "ROLE_" + rn;
      authorities.add(new SimpleGrantedAuthority(rn));
    }
    return authorities;
  }

  private void write401(HttpServletResponse response, String msg) throws IOException {
    if (response.isCommitted()) return;
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setHeader("X-Auth-Error", msg); // facilita diagnóstico no front
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    new ObjectMapper().writeValue(response.getOutputStream(), Map.of("error", msg));
  }
}