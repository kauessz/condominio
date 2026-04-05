package com.example.condo.security;

import com.example.condo.exception.TenantMismatchException;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
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

  /**
   * Apenas estes endpoints públicos de auth ignoram o token.
   * IMPORTANTE: /api/auth/me NÃO está aqui — precisa do JWT para identificar o usuário.
   */
  private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
      "/api/auth/login",
      "/api/auth/register",
      "/api/auth/refresh",
      "/api/auth/request-reset",
      "/api/auth/reset",
      "/auth/login",
      "/auth/register",
      "/auth/refresh",
      "/api/onboarding/request",
      "/onboarding/request"
  );

  @Value("${app.jwt.secret:}")
  private String jwtSecret;

  @Value("${app.jwt.issuer:condo}")
  private String issuer;

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

    if (PUBLIC_AUTH_PATHS.contains(path)) {
      filterChain.doFilter(request, response);
      return;
    }

    String auth = request.getHeader("Authorization");
    if (auth == null || !auth.startsWith("Bearer ")) {
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
      Jws<Claims> jws = Jwts.parserBuilder()
          .setSigningKey(key)
          .setAllowedClockSkewSeconds(60)
          .build()
          .parseClaimsJws(token);

      Claims claims = jws.getBody();

      String iss = claims.getIssuer();
      if (issuer != null && !issuer.isBlank() && !Objects.equals(issuer, iss)) {
        log.warn("JWT: issuer inválido. Esperado='{}' Recebido='{}' (strict={})", issuer, iss, strictIssuer);
        if (strictIssuer) {
          write401(response, "bad_issuer");
          return;
        }
      }

      String subject = claims.getSubject();
      if (subject == null || subject.isBlank()) {
        log.warn("JWT: subject ausente");
        write401(response, "no_subject");
        return;
      }

      String tenantFromToken = claims.get("tenant", String.class);
      if (tenantFromToken == null || tenantFromToken.isBlank()) {
        log.warn("JWT: tenant claim ausente");
        write401(response, "tenant_missing");
        return;
      }

      String headerTenant = firstNonBlank(
          request.getHeader("X-Tenant"),
          request.getHeader("X-Tenant-ID")
      );
      if (headerTenant != null && !headerTenant.isBlank()
          && !headerTenant.equalsIgnoreCase(tenantFromToken)) {
        throw new TenantMismatchException(tenantFromToken, headerTenant);
      }

      List<SimpleGrantedAuthority> authorities = extractAuthorities(claims);
      if (authorities.isEmpty()) {
        log.debug("JWT: sem roles mapeadas; prosseguindo apenas autenticado");
      }

      // Extrai role principal para UserContext
      String primaryRole = extractPrimaryRole(claims);

      // Extrai condominiumId do JWT (null para SUPERUSER)
      Long condominiumId = extractLong(claims, "condominiumId");

      // Extrai unitId do JWT (não-null apenas para MORADOR)
      Long unitId = extractLong(claims, "unitId");

      // Extrai userId do JWT
      Long userId = extractLong(claims, "userId");

      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(subject, null, authorities);

      SecurityContextHolder.getContext().setAuthentication(authentication);

      TenantContext.set(tenantFromToken.trim());
      UserContext.set(new UserContext.Data(primaryRole, condominiumId, unitId, userId));

      try {
        filterChain.doFilter(request, response);
      } finally {
        TenantContext.clear();
        UserContext.clear();
      }

    } catch (TenantMismatchException mismatch) {
      log.warn("JWT: tenant mismatch token='{}' header='{}'",
          mismatch.getExpectedTenant(), mismatch.getActualTenant());
      write403(response, "tenant_mismatch");
    } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
      log.warn("JWT inválido: {}", e.toString());
      write401(response, "unauthorized");
    }
  }

  /**
   * Extrai a role principal do JWT (o primeiro role sem o prefixo ROLE_).
   */
  private String extractPrimaryRole(Claims claims) {
    Object rolesClaim = claims.get("roles");
    if (rolesClaim == null) rolesClaim = claims.get("role");

    if (rolesClaim instanceof Collection<?> col) {
      for (Object o : col) {
        if (o != null) {
          String r = o.toString().toUpperCase(Locale.ROOT).strip();
          return r.startsWith("ROLE_") ? r.substring(5) : r;
        }
      }
    } else if (rolesClaim != null) {
      String r = rolesClaim.toString().toUpperCase(Locale.ROOT).strip();
      return r.startsWith("ROLE_") ? r.substring(5) : r;
    }
    return null;
  }

  /**
   * Extrai um Long de um claim JWT (suporta Integer, Long e String).
   */
  private Long extractLong(Claims claims, String claimName) {
    Object value = claims.get(claimName);
    if (value == null) return null;
    try {
      if (value instanceof Integer i) return i.longValue();
      if (value instanceof Long l) return l;
      if (value instanceof String s && !s.isBlank()) return Long.parseLong(s);
    } catch (NumberFormatException ignored) {
      log.debug("JWT: claim '{}' não é um Long válido: {}", claimName, value);
    }
    return null;
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
    response.setHeader("X-Auth-Error", msg);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    new ObjectMapper().writeValue(response.getOutputStream(), Map.of("error", msg));
  }

  private void write403(HttpServletResponse response, String msg) throws IOException {
    if (response.isCommitted()) return;
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setHeader("X-Auth-Error", msg);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    new ObjectMapper().writeValue(response.getOutputStream(), Map.of("error", msg));
  }

  private String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }
}
