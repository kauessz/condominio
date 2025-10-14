package com.example.condo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Base64;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  @Value("${app.jwt.secret:}")
  private String jwtSecret;

  @Value("${app.jwt.issuer:condo}")
  private String issuer;

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
      // Sem token -> deixa seguir; endpoints protegidos devolverão 401
      filterChain.doFilter(request, response);
      return;
    }

    String token = auth.substring("Bearer ".length()).trim();
    try {
      SecretKey key = resolveKey(jwtSecret);
      if (key == null) {
        write401(response, "jwt_secret_not_configured");
        return;
      }

      Jws<Claims> jws = Jwts.parserBuilder()
          .requireIssuer(issuer)
          .setSigningKey(key)
          .build()
          .parseClaimsJws(token);

      Claims claims = jws.getBody();
      String subject = claims.getSubject(); // email
      String role = Optional.ofNullable(claims.get("role"))
          .map(Object::toString)
          .orElse("USER")
          .toUpperCase(Locale.ROOT);

      // Mapeia para ROLE_*
      String springRole = role.startsWith("ROLE_") ? role : ("ROLE_" + role);
      List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(springRole));

      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(subject, null, authorities);

      SecurityContextHolder.getContext().setAuthentication(authentication);
      filterChain.doFilter(request, response);
    } catch (Exception e) {
      // Token inválido/expirado -> 401
      write401(response, "unauthorized");
    }
  }

  private void write401(HttpServletResponse response, String msg) throws IOException {
    if (response.isCommitted()) return;
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    new ObjectMapper().writeValue(response.getOutputStream(), Map.of("error", msg));
  }
}
