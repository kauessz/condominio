package com.example.condo.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "refresh_token",
    indexes = {
        @Index(name = "rt_by_token", columnList = "token", unique = true),
        @Index(name = "rt_by_user", columnList = "tenant_id,user_id")
    }
)
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "user_id", nullable = false, length = 128)
  private String userId; // pode ser o email

  @Column(name = "token", nullable = false, unique = true, length = 256)
  private String token;  // opaco (uuid/random)

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  // ===== Getters/Setters =====
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getTenantId() { return tenantId; }
  public void setTenantId(String tenantId) { this.tenantId = tenantId; }

  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }

  public String getToken() { return token; }
  public void setToken(String token) { this.token = token; }

  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

  public Instant getRevokedAt() { return revokedAt; }
  public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}