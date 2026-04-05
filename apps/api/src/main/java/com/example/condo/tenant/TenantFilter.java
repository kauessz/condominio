package com.example.condo.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

@Component
@Order(1)
public class TenantFilter extends OncePerRequestFilter {

  @Value("${spring.profiles.active:}")
  private String activeProfile;

  @Value("${app.tenant.default:demo}")
  private String defaultTenant;

  private static String firstNonBlank(String... vals) {
    if (vals == null) return null;
    for (String v : vals) {
      if (v != null && !v.isBlank()) return v.trim();
    }
    return null;
  }

  @Override
  protected void doFilterInternal(@NonNull HttpServletRequest request,
                                  @NonNull HttpServletResponse response,
                                  @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    try {
      // aceita os dois headers
      String tenant = firstNonBlank(
          request.getHeader("X-Tenant"),
          request.getHeader("X-Tenant-ID")
      );

      // fallback em dev
      if ((tenant == null || tenant.isBlank())
          && "dev".equalsIgnoreCase(Objects.toString(activeProfile, ""))) {
        tenant = defaultTenant;
      }

      if (tenant == null || tenant.isBlank()) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return;
      }

      TenantContext.set(tenant);
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  @Override
  protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
    String p = request.getRequestURI().toLowerCase(Locale.ROOT);
    return p.startsWith("/swagger") || p.startsWith("/v3/api-docs") || p.startsWith("/actuator");
  }
}