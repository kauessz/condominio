package com.example.condo.web;

import com.example.condo.entity.User;
import com.example.condo.security.JwtUtils;
import com.example.condo.service.AuthService;
import com.example.condo.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/auth", "/api/auth"})
public class AuthController {

  @Value("${app.jwt.secret}") private String secret;
  @Value("${app.jwt.issuer}") private String issuer;
  @Value("${app.jwt.expirationMinutes}") private Integer expiration;

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  public static class LoginReq {
    public String email;
    public String password;
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginReq req,
                                 @RequestHeader(value = "X-Tenant", required = false) String tenantHeader) {
    User user = auth.authenticate(req.email, req.password);
    if (user == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
    }
    String role = user.getRole();
    String token = JwtUtils.createToken(user.getEmail(), role, issuer, secret, expiration);
    return ResponseEntity.ok(Map.of(
        "token", token,
        "role", role,
        "tenant", TenantContext.get()
    ));
  }

  @GetMapping("/me")
  public ResponseEntity<?> me(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
    }
    String email = (String) authentication.getPrincipal();
    String role = authentication.getAuthorities().stream()
        .findFirst()
        .map(GrantedAuthority::getAuthority)
        .orElse("ROLE_RESIDENT")
        .replace("ROLE_", "");

    return ResponseEntity.ok(Map.of(
        "email", email,
        "role", role,
        "tenant", TenantContext.get()
    ));
  }
}