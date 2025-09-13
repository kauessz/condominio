package com.example.condo.model;

import com.example.condo.tenant.TenantContext;
import jakarta.persistence.PrePersist;

public class TenantEntityListener {
    @PrePersist
    public void onCreate(BaseEntity e) {
        if (e.getTenantId() == null || e.getTenantId().isBlank()) {
            e.setTenantId(TenantContext.get() == null ? "default" : TenantContext.get());
        }
    }
}