package com.example.condo.bootstrap;

import com.example.condo.entity.User;
import com.example.condo.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevAdminSeed implements CommandLineRunner {
  private final UserRepository users;
  private final PasswordEncoder encoder;

  public DevAdminSeed(UserRepository users, PasswordEncoder encoder) {
    this.users = users;
    this.encoder = encoder;
  }

  @Override
  public void run(String... args) {
    users.findByTenantAndEmail("demo", "admin@demo.com").ifPresentOrElse(
      u -> {}, // já existe
      () -> {
        User admin = new User();
        admin.setTenantId("demo");
        admin.setEmail("admin@demo.com");
        admin.setPasswordHash(encoder.encode("admin123"));
        admin.setRole("ADMIN");
        users.save(admin);
        System.out.println("[seed] admin@demo.com criado (tenant=demo, senha=admin123)");
      }
    );
  }
}