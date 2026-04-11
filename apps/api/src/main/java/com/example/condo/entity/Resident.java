package com.example.condo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "resident")
public class Resident {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private String tenantId;

  @Column(name = "condominium_id", nullable = false)
  private Long condominiumId;

  @Column(name = "unit_id")
  private Long unitId; // opcional

  @Column(name = "user_id")
  private Long userId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String email;

  @Column(nullable = false)
  private String phone;

  /** CPF do morador (somente dígitos, sem máscara). Opcional. */
  @Column(length = 14)
  private String cpf;

  // getters/setters
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getTenantId() { return tenantId; }
  public void setTenantId(String tenantId) { this.tenantId = tenantId; }

  public Long getCondominiumId() { return condominiumId; }
  public void setCondominiumId(Long condominiumId) { this.condominiumId = condominiumId; }

  public Long getUnitId() { return unitId; }
  public void setUnitId(Long unitId) { this.unitId = unitId; }

  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }

  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }

  public String getCpf() { return cpf; }
  public void setCpf(String cpf) { this.cpf = cpf; }
}
