package com.example.condo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  @Value("${app.jwt.secret}")
  private String secret;

  private static byte[] resolveSecretBytes(String secret) {
    if (secret != null && secret.startsWith("base64:")) {
      String b64 = secret.substring("base64:".length());
      return Decoders.BASE64.decode(b64);
    }
    return secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected void doFilterInternal(@NonNull HttpServletRequest request,
                                  @NonNull HttpServletResponse response,
                                  @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      try {
        SecretKey key = Keys.hmacShaKeyFor(resolveSecretBytes(secret));
        Jws<Claims> jws = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token);

        String subject = jws.getBody().getSubject();
        String role = Optional.ofNullable((String) jws.getBody().get("role")).orElse("RESIDENT");

        // Concede ROLE_<role> e <role> (cobre hasRole/hasAuthority)
        List<SimpleGrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + role),
            new SimpleGrantedAuthority(role)
        );

        var auth = new UsernamePasswordAuthenticationToken(subject, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (Exception ignored) {
        // Token inválido/expirado: segue sem autenticação
      }
    }

    filterChain.doFilter(request, response);
  }
}