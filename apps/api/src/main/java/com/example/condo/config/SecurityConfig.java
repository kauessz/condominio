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
import org.springframework.security.config.http.SessionCreationPolicy;

import java.util.List;

/**
 * Configuração de segurança do CondoHub.
 *
 * Roles: SUPERUSER > ADMIN > SINDICO > FINANCEIRO > OPERADOR > ZELADOR > PORTARIA > MORADOR
 *
 * Regra de isolamento:
 *   - SUPERUSER: sem filtro de tenant (vê todos os condomínios)
 *   - Todos os outros: condominiumId SEMPRE do JWT, nunca de query/body
 */
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
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setHeader("X-Auth-Error", "unauthorized");
                    res.setContentType("application/json");
                    res.getWriter().write(
                        "{\"error\":\"unauthorized\",\"message\":\"Token inv\\u00e1lido ou ausente\"}");
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(403);
                    res.setHeader("X-Auth-Error", "forbidden");
                    res.setContentType("application/json");
                    res.getWriter().write(
                        "{\"error\":\"forbidden\",\"message\":\"Voc\\u00ea n\\u00e3o tem permiss\\u00e3o para acessar este recurso\"}");
                }))

            .authorizeHttpRequests(auth -> auth

                // ===== Preflight CORS =====
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ===== Auth público =====
                .requestMatchers(
                    "/api/auth/login", "/api/auth/register",
                    "/api/auth/refresh", "/api/auth/request-reset", "/api/auth/reset",
                    "/auth/login", "/auth/register", "/auth/refresh",
                    "/actuator/health"
                ).permitAll()

                // ===== Onboarding — formulário público (sem autenticação) =====
                .requestMatchers(HttpMethod.POST,
                    "/api/onboarding/request", "/onboarding/request"
                ).permitAll()

                // ===== Auth me =====
                .requestMatchers("/api/auth/me", "/auth/me").authenticated()

                // ===== Onboarding — painel SUPERUSER =====
                .requestMatchers(
                    "/api/onboarding/**", "/onboarding/**"
                ).hasRole("SUPERUSER")

                // ===== Condomínios =====
                .requestMatchers(HttpMethod.GET,
                    "/api/condominiums", "/api/condominiums/**",
                    "/condominiums", "/condominiums/**"
                ).authenticated()

                .requestMatchers(HttpMethod.POST,
                    "/api/condominiums", "/api/condominiums/**",
                    "/condominiums", "/condominiums/**"
                ).hasRole("SUPERUSER")

                .requestMatchers(HttpMethod.PUT,
                    "/api/condominiums/**", "/condominiums/**"
                ).hasRole("SUPERUSER")

                .requestMatchers(HttpMethod.DELETE,
                    "/api/condominiums/**", "/condominiums/**"
                ).hasRole("SUPERUSER")

                // ===== Governança / Aprovação =====
                .requestMatchers(HttpMethod.GET,
                    "/api/governance/requests", "/api/governance/requests/**",
                    "/governance/requests", "/governance/requests/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO")
                .requestMatchers(HttpMethod.POST,
                    "/api/governance/requests", "/governance/requests"
                ).hasAnyRole("ADMIN", "SINDICO")
                .requestMatchers(HttpMethod.POST,
                    "/api/governance/requests/*/approve", "/api/governance/requests/*/reject",
                    "/governance/requests/*/approve", "/governance/requests/*/reject"
                ).hasRole("SUPERUSER")
                .requestMatchers(HttpMethod.POST,
                    "/api/governance/requests/*/cancel", "/governance/requests/*/cancel"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO")

                // ===== Unidades =====
                // GET: todos os roles (PORTARIA e MORADOR precisam para dropdowns)
                .requestMatchers(HttpMethod.GET,
                    "/api/units", "/api/units/**",
                    "/units", "/units/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "ZELADOR", "PORTARIA", "MORADOR")

                // Escrita: somente gestores
                .requestMatchers(HttpMethod.POST,
                    "/api/units", "/api/units/**",
                    "/units", "/units/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO")

                .requestMatchers(HttpMethod.PUT,
                    "/api/units/**", "/units/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO")

                .requestMatchers(HttpMethod.DELETE,
                    "/api/units/**", "/units/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO")

                // ===== Moradores =====
                // GET: gestores + ZELADOR (OS) + PORTARIA (verificação)
                .requestMatchers(HttpMethod.GET,
                    "/api/residents", "/api/residents/**",
                    "/residents", "/residents/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "OPERADOR", "ZELADOR", "PORTARIA", "MORADOR")

                // Escrita: somente gestores
                .requestMatchers(HttpMethod.POST,
                    "/api/residents", "/api/residents/**",
                    "/residents", "/residents/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "MORADOR")

                .requestMatchers(HttpMethod.PUT,
                    "/api/residents/**", "/residents/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "MORADOR")

                .requestMatchers(HttpMethod.DELETE,
                    "/api/residents/**", "/residents/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO")

                // ===== Visitantes — leitura =====
                // MORADOR vê só sua unidade (filtrado no service)
                // ADMIN/SINDICO/ZELADOR veem apenas entregas (filtrado no service)
                // PORTARIA vê tudo do condomínio
                .requestMatchers(HttpMethod.GET,
                    "/api/visitors", "/api/visitors/**",
                    "/visitors", "/visitors/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "OPERADOR", "ZELADOR", "PORTARIA", "MORADOR")

                // ===== Visitantes — criação =====
                // Criação de visita pessoal: PORTARIA, MORADOR
                // Criação de entrega: PORTARIA
                // Controller/service fazem validação por tipo
                .requestMatchers(HttpMethod.POST,
                    "/api/visitors", "/visitors"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "PORTARIA", "MORADOR")

                // ===== Visitantes — aprovação =====
                .requestMatchers(HttpMethod.POST,
                    "/api/visitors/*/approve", "/api/visitors/*/reject",
                    "/visitors/*/approve", "/visitors/*/reject"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "MORADOR")

                // ===== Visitantes — checkout legado =====
                .requestMatchers(HttpMethod.POST,
                    "/api/visitors/*/checkout", "/visitors/*/checkout"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "PORTARIA")

                // ===== Visitantes — outras ações POST =====
                .requestMatchers(HttpMethod.POST,
                    "/api/visitors/**", "/visitors/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "PORTARIA", "MORADOR")

                // ===== Visitantes — atualização de status via PATCH =====
                .requestMatchers(HttpMethod.PATCH,
                    "/api/visitors/**", "/visitors/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "PORTARIA", "MORADOR")

                // ===== Visitantes — edição/exclusão =====
                .requestMatchers(HttpMethod.PUT,
                    "/api/visitors/**", "/visitors/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "PORTARIA")

                .requestMatchers(HttpMethod.DELETE,
                    "/api/visitors/**", "/visitors/**"
                ).hasAnyRole("SUPERUSER", "ADMIN")

                // ===== Usuários — leitura =====
                // SUPERUSER vê todos; ADMIN/SINDICO veem apenas do próprio condo
                .requestMatchers(HttpMethod.GET,
                    "/api/users", "/api/users/**",
                    "/users", "/users/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO")

                // ===== Auditoria =====
                .requestMatchers(HttpMethod.GET,
                    "/api/audit", "/api/audit/**",
                    "/audit", "/audit/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO")

                // ===== Usuários — criação =====
                // SUPERUSER pode criar em qualquer condo; ADMIN/SINDICO criam no próprio condo
                .requestMatchers(HttpMethod.POST,
                    "/api/users", "/users"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO")

                // ===== Usuários — atualização de role/condomínio =====
                // Exclusivo SUPERUSER (ajuste de role e vínculo de condomínio)
                .requestMatchers(HttpMethod.PUT,
                    "/api/users/**", "/users/**"
                ).hasRole("SUPERUSER")

                // ===== Usuários — exclusão =====
                .requestMatchers(HttpMethod.DELETE,
                    "/api/users/**", "/users/**"
                ).hasRole("SUPERUSER")

                // ===== Áreas Comuns =====
                .requestMatchers(HttpMethod.GET,
                    "/api/common-areas", "/api/common-areas/**"
                ).authenticated()
                .requestMatchers(HttpMethod.POST,
                    "/api/common-areas", "/api/common-areas/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO")
                .requestMatchers(HttpMethod.PUT,
                    "/api/common-areas/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO")
                .requestMatchers(HttpMethod.DELETE,
                    "/api/common-areas/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO")

                // ===== Reservas =====
                .requestMatchers(HttpMethod.GET,
                    "/api/reservations", "/api/reservations/**"
                ).authenticated()
                .requestMatchers(HttpMethod.POST,
                    "/api/reservations"
                ).authenticated()
                .requestMatchers(HttpMethod.PATCH,
                    "/api/reservations/**"
                ).authenticated()

                // ===== Ordens de Serviço =====
                // OPERADOR incluído explicitamente em leitura (cobertura operacional)
                .requestMatchers(HttpMethod.GET,
                    "/api/work-orders", "/api/work-orders/**",
                    "/api/work-order-categories", "/api/work-order-categories/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "FINANCEIRO", "OPERADOR", "ZELADOR", "PORTARIA", "MORADOR")
                .requestMatchers(HttpMethod.POST,
                    "/api/work-orders"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "OPERADOR", "ZELADOR", "PORTARIA", "MORADOR")
                .requestMatchers(HttpMethod.PATCH,
                    "/api/work-orders/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "OPERADOR", "ZELADOR")

                // ===== Vagas / Sorteio =====
                .requestMatchers(
                    "/api/parking/**"
                ).authenticated()

                // ===== Assembleias =====
                .requestMatchers(HttpMethod.GET,
                    "/api/assemblies/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "MORADOR")
                .requestMatchers(HttpMethod.POST,
                    "/api/assemblies/*/agenda/*/vote"
                ).hasAnyRole("MORADOR", "SINDICO")
                .requestMatchers(HttpMethod.POST,
                    "/api/assemblies/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO")
                .requestMatchers(HttpMethod.PATCH,
                    "/api/assemblies/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO")

                // ===== Financeiro =====
                .requestMatchers(HttpMethod.GET,
                    "/api/financial/invoices", "/api/financial/invoices/**"
                ).authenticated()
                .requestMatchers(HttpMethod.POST,
                    "/api/financial/webhooks/asaas",
                    "/api/financial/webhooks/asaas/**",
                    "/api/payments/webhooks/payments"
                ).permitAll()
                .requestMatchers(HttpMethod.GET,
                    "/api/financial/config"
                ).authenticated()

                .requestMatchers(HttpMethod.GET,
                    "/api/financial/summary"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "FINANCEIRO", "MORADOR")
                .requestMatchers(HttpMethod.PUT,
                    "/api/financial/config"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "FINANCEIRO")
                .requestMatchers(HttpMethod.PATCH,
                    "/api/financial/invoices/**"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "FINANCEIRO")
                .requestMatchers(HttpMethod.POST,
                    "/api/financial/invoices/*/external-charge"
                ).hasAnyRole("SUPERUSER", "ADMIN", "SINDICO", "FINANCEIRO")

                // ===== Qualquer outra rota: autenticado =====
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        var c = new CorsConfiguration();
        c.setAllowedOrigins(List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:3000",
            "http://127.0.0.1:3000"
        ));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setExposedHeaders(List.of("Authorization", "X-Auth-Error"));
        c.setAllowCredentials(false);
        c.setMaxAge(3600L);

        var s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/**", c);
        return s;
    }
}
