package com.example.condo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(0) // roda antes dos demais filtros
public class DebugHeaderFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain
  ) throws ServletException, IOException {

    if (request.getRequestURI().startsWith("/api/units")) {
      System.out.println("[DBG] " + request.getMethod() + " " + request.getRequestURI());
      System.out.println("[DBG] Authorization: " + request.getHeader("Authorization"));
      System.out.println("[DBG] X-Tenant: " + request.getHeader("X-Tenant"));
    }

    chain.doFilter(request, response);
  }
}