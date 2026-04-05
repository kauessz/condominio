package com.example.condo.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "common_area")
public class CommonArea {

    public enum ReservationApprovalMode { AUTOMATIC, REQUIRE_APPROVAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "condominium_id", nullable = false)
    private Long condominiumId;

    @Column(nullable = false)
    private String name;

    private Integer capacity;

    private String rules;

    @Column(name = "max_hours_per_reservation", nullable = false)
    private int maxHoursPerReservation = 4;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval = false;

    @Column(name = "allowed_start_hour")
    private Integer allowedStartHour;

    @Column(name = "allowed_end_hour")
    private Integer allowedEndHour;

    @Column(name = "reservation_description")
    private String reservationDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_approval_mode")
    private ReservationApprovalMode reservationApprovalMode;

    @Column(name = "allow_override_from_condominium_default", nullable = false)
    private boolean allowOverrideFromCondominiumDefault = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Long getCondominiumId() { return condominiumId; }
    public void setCondominiumId(Long condominiumId) { this.condominiumId = condominiumId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public String getRules() { return rules; }
    public void setRules(String rules) { this.rules = rules; }

    public int getMaxHoursPerReservation() { return maxHoursPerReservation; }
    public void setMaxHoursPerReservation(int maxHoursPerReservation) { this.maxHoursPerReservation = maxHoursPerReservation; }

    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(boolean requiresApproval) { this.requiresApproval = requiresApproval; }

    public Integer getAllowedStartHour() { return allowedStartHour; }
    public void setAllowedStartHour(Integer allowedStartHour) { this.allowedStartHour = allowedStartHour; }

    public Integer getAllowedEndHour() { return allowedEndHour; }
    public void setAllowedEndHour(Integer allowedEndHour) { this.allowedEndHour = allowedEndHour; }

    public String getReservationDescription() { return reservationDescription; }
    public void setReservationDescription(String reservationDescription) { this.reservationDescription = reservationDescription; }

    public ReservationApprovalMode getReservationApprovalMode() { return reservationApprovalMode; }
    public void setReservationApprovalMode(ReservationApprovalMode reservationApprovalMode) {
        this.reservationApprovalMode = reservationApprovalMode;
    }

    public boolean isAllowOverrideFromCondominiumDefault() { return allowOverrideFromCondominiumDefault; }
    public void setAllowOverrideFromCondominiumDefault(boolean allowOverrideFromCondominiumDefault) {
        this.allowOverrideFromCondominiumDefault = allowOverrideFromCondominiumDefault;
    }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
