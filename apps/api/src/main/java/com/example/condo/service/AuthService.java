package com.example.condo.service;

import com.example.condo.entity.User;
import com.example.condo.repo.UserRepository;
import com.example.condo.tenant.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final UserRepository users;
  private final PasswordEncoder encoder;

  public AuthService(UserRepository users, PasswordEncoder encoder) {
    this.users = users;
    this.encoder = encoder;
  }

  public User authenticate(String email, String rawPassword) {
    String tenant = TenantContext.get();
    if (tenant == null || tenant.isBlank()) {
      // sem tenant não há como autenticar
      return null;
    }

    // use o nome que EXISTE no seu UserRepository:
    return users.findByTenantAndEmail(tenant, email)
        .filter(u -> encoder.matches(rawPassword, u.getPasswordHash()))
        .orElse(null);
  }
}