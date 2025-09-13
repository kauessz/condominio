package com.example.condo.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
@EntityListeners(TenantEntityListener.class)
public abstract class BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    protected String tenantId;
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}