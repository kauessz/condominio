package com.example.condo.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "financial_config")
public class FinancialConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "condominium_id", nullable = false, unique = true)
    private Long condominiumId;

    @Column(name = "monthly_fee", nullable = false)
    private BigDecimal monthlyFee = BigDecimal.ZERO;

    @Column(name = "due_day", nullable = false)
    private int dueDay = 10;

    @Column(name = "late_fee_pct", nullable = false)
    private BigDecimal lateFeePct = new BigDecimal("2.00");

    @Column(name = "interest_pct", nullable = false)
    private BigDecimal interestPct = new BigDecimal("1.00");

    @Column(name = "pix_key")
    private String pixKey;

    @Column(name = "pix_key_type")
    private String pixKeyType;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Long getCondominiumId() { return condominiumId; }
    public void setCondominiumId(Long condominiumId) { this.condominiumId = condominiumId; }

    public BigDecimal getMonthlyFee() { return monthlyFee; }
    public void setMonthlyFee(BigDecimal monthlyFee) { this.monthlyFee = monthlyFee; }

    public int getDueDay() { return dueDay; }
    public void setDueDay(int dueDay) { this.dueDay = dueDay; }

    public BigDecimal getLateFeePct() { return lateFeePct; }
    public void setLateFeePct(BigDecimal lateFeePct) { this.lateFeePct = lateFeePct; }

    public BigDecimal getInterestPct() { return interestPct; }
    public void setInterestPct(BigDecimal interestPct) { this.interestPct = interestPct; }

    public String getPixKey() { return pixKey; }
    public void setPixKey(String pixKey) { this.pixKey = pixKey; }

    public String getPixKeyType() { return pixKeyType; }
    public void setPixKeyType(String pixKeyType) { this.pixKeyType = pixKeyType; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
