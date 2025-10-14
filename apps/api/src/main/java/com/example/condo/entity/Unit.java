package com.example.condo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "unit")
public class Unit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "condominium_id", nullable = false)
  private Long condominiumId;

  // >>> NOVO: code NOT NULL (alinha com a coluna do banco)
  @Column(name = "code", nullable = false, length = 128)
  private String code;

  @Column(name = "number", nullable = false, length = 64)
  private String number;

  @Column(name = "block", length = 64)
  private String block;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @PrePersist
  public void prePersist() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }

  // getters / setters

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getTenantId() { return tenantId; }
  public void setTenantId(String tenantId) { this.tenantId = tenantId; }

  public Long getCondominiumId() { return condominiumId; }
  public void setCondominiumId(Long condominiumId) { this.condominiumId = condominiumId; }

  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }

  public String getNumber() { return number; }
  public void setNumber(String number) { this.number = number; }

  public String getBlock() { return block; }
  public void setBlock(String block) { this.block = block; }

  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
