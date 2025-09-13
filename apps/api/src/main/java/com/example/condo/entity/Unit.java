package com.example.condo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "unit")
public class Unit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private String tenantId;

  @Column(name = "condominium_id", nullable = false)
  private Long condominiumId;

  @Column(name = "number", nullable = false)
  private String number;

  // usamos string vazia para evitar nulos e facilitar igualdade
  @Column(name = "block", nullable = false)
  private String block = "";

  // getters/setters
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getTenantId() { return tenantId; }
  public void setTenantId(String tenantId) { this.tenantId = tenantId; }

  public Long getCondominiumId() { return condominiumId; }
  public void setCondominiumId(Long condominiumId) { this.condominiumId = condominiumId; }

  public String getNumber() { return number; }
  public void setNumber(String number) { this.number = number; }

  public String getBlock() { return block; }
  public void setBlock(String block) { this.block = (block == null ? "" : block); }
}