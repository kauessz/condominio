package com.example.condo.config;

import com.example.condo.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthFilter jwtAuthFilter;

  public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
    this.jwtAuthFilter = jwtAuthFilter;
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())

        .exceptionHandling(e -> e
            .authenticationEntryPoint((req, res, ex) -> {
              res.setStatus(401);
              res.setContentType("application/json");
              res.getWriter().write("{\"error\":\"unauthorized\"}");
            })
            .accessDeniedHandler((req, res, ex) -> {
              res.setStatus(403);
              res.setContentType("application/json");
              res.getWriter().write("{\"error\":\"forbidden\"}");
            }))

        .authorizeHttpRequests(auth -> auth
            // Preflight
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

            // Público (login/health)
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers("/auth/**").permitAll()
            .requestMatchers("/actuator/health").permitAll()

            // ===== Leitura autenticada =====
            .requestMatchers(HttpMethod.GET,
                "/api/condominiums/*/counters",
                "/condominiums/*/counters",
                "/api/condominiums", "/api/condominiums/**",
                "/condominiums", "/condominiums/**",
                "/api/units", "/api/units/**",
                "/units", "/units/**",
                "/api/residents", "/api/residents/**",
                "/residents", "/residents/**",
                "/api/visitors", "/api/visitors/**",
                "/visitors", "/visitors/**"
            ).authenticated()

            // ===== Escrita somente ADMIN =====
            .requestMatchers(HttpMethod.POST,
                "/api/condominiums/**", "/api/units/**",
                "/api/residents/**", "/api/visitors/**",
                "/condominiums/**", "/units/**",
                "/residents/**", "/visitors/**"
            ).hasRole("ADMIN")

            .requestMatchers(HttpMethod.PUT,
                "/api/condominiums/**", "/api/units/**",
                "/api/residents/**", "/api/visitors/**",
                "/condominiums/**", "/units/**",
                "/residents/**", "/visitors/**"
            ).hasRole("ADMIN")

            .requestMatchers(HttpMethod.PATCH,
                "/api/condominiums/**", "/api/units/**",
                "/api/residents/**", "/api/visitors/**",
                "/condominiums/**", "/units/**",
                "/residents/**", "/visitors/**"
            ).hasRole("ADMIN")

            .requestMatchers(HttpMethod.DELETE,
                "/api/condominiums/**", "/api/units/**",
                "/api/residents/**", "/api/visitors/**",
                "/condominiums/**", "/units/**",
                "/residents/**", "/visitors/**"
            ).hasRole("ADMIN")

            .anyRequest().denyAll()
        )
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    var c = new CorsConfiguration();
    // Ajuste as origens conforme seu ambiente
    c.setAllowedOrigins(List.of(
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://192.168.0.122:5173",
        "https://condo-landing.netlify.app"
    ));
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(List.of("*"));
    // expõe também o X-Auth-Error para o front saber o motivo de 401
    c.setExposedHeaders(List.of("Authorization", "X-Auth-Error"));
    c.setAllowCredentials(false);
    c.setMaxAge(3600L);

    var s = new UrlBasedCorsConfigurationSource();
    s.registerCorsConfiguration("/**", c);
    return s;
  }
}