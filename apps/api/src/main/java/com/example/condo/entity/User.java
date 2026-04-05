package com.example.condo.entity;

import com.example.condo.model.BaseEntity;
import com.example.condo.persistence.RoleCodeConverter;
import com.example.condo.security.Role;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(nullable = false, unique = true, length = 200)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Convert(converter = RoleCodeConverter.class)
  @Column(nullable = false, length = 32)
  private Role role;

  @Column(name = "name", length = 255)
  private String name;

  @Column(name = "created_at", updatable = false, insertable = false)
  private LocalDateTime createdAt;

  /**
   * ID do condomínio do usuário.
   * Obrigatório para SINDICO, ADMIN, PORTARIA, MORADOR.
   * Nulo para SUPERUSER (acesso total a todos os condomínios).
   */
  @Column(name = "condominium_id")
  private Long condominiumId;

  /**
   * ID da unidade do usuário.
   * Obrigatório para MORADOR — vincula o usuário à sua unidade.
   * Nulo para SUPERUSER, SINDICO, ADMIN, PORTARIA.
   */
  @Column(name = "unit_id")
  private Long unitId;

  // GETTERS / SETTERS

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getTenantId() { return tenantId; }
  public void setTenantId(String tenantId) { this.tenantId = tenantId; }

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }

  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

  public Role getRole() { return role; }
  public void setRole(Role role) { this.role = role; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

  public Long getCondominiumId() { return condominiumId; }
  public void setCondominiumId(Long condominiumId) { this.condominiumId = condominiumId; }

  public Long getUnitId() { return unitId; }
  public void setUnitId(Long unitId) { this.unitId = unitId; }

  /**
   * Se true, o usuário deve trocar a senha no próximo login.
   * Usado ao criar contas via onboarding (senha temporária).
   */
  @Column(name = "must_change_password", nullable = false)
  private boolean mustChangePassword = false;

  public boolean isMustChangePassword() { return mustChangePassword; }
  public void setMustChangePassword(boolean mustChangePassword) {
    this.mustChangePassword = mustChangePassword;
  }
}
