package com.example.condo.entity;

import com.example.condo.model.BaseEntity;
import com.example.condo.security.Role;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // multi-tenant: qual condomínio / cliente
  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(nullable = false, unique = true, length = 200)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  // usamos EnumType.STRING pra gravar ADMIN, RESIDENT, etc. como texto
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private Role role;

  // nome do usuário pra exibir na UI
  @Column(name = "name", length = 255)
  private String name;

  // timestamp de criação da linha. Banco já preenche com now()
  // se o seu BaseEntity JÁ tiver createdAt mapeado, remove esse campo daqui pra evitar coluna duplicada.
  @Column(name = "created_at", updatable = false, insertable = false)
  private LocalDateTime createdAt;

  // GETTERS / SETTERS

  public Long getId() {
    return id;
  }
  public void setId(Long id) {
    this.id = id;
  }

  public String getTenantId() {
    return tenantId;
  }
  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getEmail() {
    return email;
  }
  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }
  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public Role getRole() {
    return role;
  }
  public void setRole(Role role) {
    this.role = role;
  }

  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  // opcional futuro:
  // public Long getUnitId() { ... }
  // se você adicionar esse campo (por exemplo, FK da unidade do morador),
  // o /me vai conseguir devolver unitId direto sem reflection.
}