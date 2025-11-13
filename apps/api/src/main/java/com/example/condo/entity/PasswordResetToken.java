package com.example.condo.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 128)
  private String token;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(nullable = false, length = 320)
  private String email;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getToken() { return token; }
  public void setToken(String token) { this.token = token; }

  public String getTenantId() { return tenantId; }
  public void setTenantId(String tenantId) { this.tenantId = tenantId; }

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

  public Instant getUsedAt() { return usedAt; }
  public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
}