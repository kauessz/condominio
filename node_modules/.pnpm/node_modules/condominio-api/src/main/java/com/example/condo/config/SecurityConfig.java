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
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/auth/**").permitAll()

            // leitura autenticada
            .requestMatchers(HttpMethod.GET, "/api/**").authenticated()

            // escrita: admin (ajuste seus papéis se tiver "MANAGER" etc.)
            .requestMatchers(HttpMethod.POST, "/api/units/**", "/api/residents/**", "/api/condominiums/**")
            .hasRole("ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/units/**", "/api/residents/**", "/api/condominiums/**")
            .hasRole("ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/units/**", "/api/residents/**", "/api/condominiums/**")
            .hasRole("ADMIN")

            .anyRequest().denyAll())
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    var c = new CorsConfiguration();
    c.setAllowedOrigins(List.of("http://localhost:5173")); // porta do seu Vite
    c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization","Content-Type","X-Requested-With"));
    c.setExposedHeaders(List.of("Authorization"));
    c.setAllowCredentials(true);

    var s = new UrlBasedCorsConfigurationSource();
    s.registerCorsConfiguration("/**", c);
    return s;
  }
}