package com.example.condo.entity;

import com.example.condo.model.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Condominium extends BaseEntity {

    public enum ParkingPolicyMode { FIXED, DRAW }
    public enum ParkingDrawFrequency { MONTHLY, QUARTERLY, SEMIANNUAL, YEARLY, CUSTOM }
    public enum ReservationPolicyMode { FLEXIBLE_INTERVAL, FIXED_WINDOW }
    public enum ReservationApprovalMode { AUTOMATIC, REQUIRE_APPROVAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String cnpj;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false, insertable = false)
    private LocalDateTime createdAt;

    /**
     * Tempo (em minutos) que uma visita pode ficar em status PENDING
     * antes de ser expirada automaticamente.
     * Padrão: 15 minutos.
     */
    @Column(name = "visitor_pending_timeout_minutes", nullable = false)
    private int visitorPendingTimeoutMinutes = 15;

    @Column(name = "allow_syndic_approve_visitor", nullable = false)
    private boolean allowSyndicApproveVisitor = false;

    @Column(name = "resident_approval_required", nullable = false)
    private boolean residentApprovalRequired = true;

    @Column(name = "admin_override_allowed", nullable = false)
    private boolean adminOverrideAllowed = true;

    @Column(name = "portaria_can_auto_approve", nullable = false)
    private boolean portariaCanAutoApprove = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "parking_policy_mode", nullable = false)
    private ParkingPolicyMode parkingPolicyMode = ParkingPolicyMode.DRAW;

    @Enumerated(EnumType.STRING)
    @Column(name = "parking_draw_frequency", nullable = false)
    private ParkingDrawFrequency parkingDrawFrequency = ParkingDrawFrequency.QUARTERLY;

    @Column(name = "draw_interval_months")
    private Integer drawIntervalMonths;

    @Column(name = "allow_manual_assignments", nullable = false)
    private boolean allowManualAssignments = true;

    @Column(name = "allow_resident_registration", nullable = false)
    private boolean allowResidentRegistration = true;

    @Column(name = "max_vehicles_per_unit", nullable = false)
    private int maxVehiclesPerUnit = 1;

    @Column(name = "parking_rules")
    private String parkingRules;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_policy_mode", nullable = false)
    private ReservationPolicyMode reservationPolicyMode = ReservationPolicyMode.FLEXIBLE_INTERVAL;

    @Column(name = "default_max_duration_hours", nullable = false)
    private int defaultMaxDurationHours = 4;

    @Column(name = "default_start_hour", nullable = false)
    private int defaultStartHour = 8;

    @Column(name = "default_end_hour", nullable = false)
    private int defaultEndHour = 22;

    @Column(name = "all_day_reservation_allowed", nullable = false)
    private boolean allDayReservationAllowed = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_approval_mode", nullable = false)
    private ReservationApprovalMode reservationApprovalMode = ReservationApprovalMode.AUTOMATIC;

    @Column(name = "reservation_rules")
    private String reservationRules;

    // Getters / Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getVisitorPendingTimeoutMinutes() { return visitorPendingTimeoutMinutes; }
    public void setVisitorPendingTimeoutMinutes(int visitorPendingTimeoutMinutes) {
        this.visitorPendingTimeoutMinutes = visitorPendingTimeoutMinutes;
    }

    public boolean isAllowSyndicApproveVisitor() { return allowSyndicApproveVisitor; }
    public void setAllowSyndicApproveVisitor(boolean allowSyndicApproveVisitor) {
        this.allowSyndicApproveVisitor = allowSyndicApproveVisitor;
    }

    public boolean isResidentApprovalRequired() { return residentApprovalRequired; }
    public void setResidentApprovalRequired(boolean residentApprovalRequired) {
        this.residentApprovalRequired = residentApprovalRequired;
    }

    public boolean isAdminOverrideAllowed() { return adminOverrideAllowed; }
    public void setAdminOverrideAllowed(boolean adminOverrideAllowed) {
        this.adminOverrideAllowed = adminOverrideAllowed;
    }

    public boolean isPortariaCanAutoApprove() { return portariaCanAutoApprove; }
    public void setPortariaCanAutoApprove(boolean portariaCanAutoApprove) {
        this.portariaCanAutoApprove = portariaCanAutoApprove;
    }

    public ParkingPolicyMode getParkingPolicyMode() { return parkingPolicyMode; }
    public void setParkingPolicyMode(ParkingPolicyMode parkingPolicyMode) {
        this.parkingPolicyMode = parkingPolicyMode;
    }

    public ParkingDrawFrequency getParkingDrawFrequency() { return parkingDrawFrequency; }
    public void setParkingDrawFrequency(ParkingDrawFrequency parkingDrawFrequency) {
        this.parkingDrawFrequency = parkingDrawFrequency;
    }

    public Integer getDrawIntervalMonths() { return drawIntervalMonths; }
    public void setDrawIntervalMonths(Integer drawIntervalMonths) {
        this.drawIntervalMonths = drawIntervalMonths;
    }

    public boolean isAllowManualAssignments() { return allowManualAssignments; }
    public void setAllowManualAssignments(boolean allowManualAssignments) {
        this.allowManualAssignments = allowManualAssignments;
    }

    public boolean isAllowResidentRegistration() { return allowResidentRegistration; }
    public void setAllowResidentRegistration(boolean allowResidentRegistration) {
        this.allowResidentRegistration = allowResidentRegistration;
    }

    public int getMaxVehiclesPerUnit() { return maxVehiclesPerUnit; }
    public void setMaxVehiclesPerUnit(int maxVehiclesPerUnit) {
        this.maxVehiclesPerUnit = maxVehiclesPerUnit;
    }

    public String getParkingRules() { return parkingRules; }
    public void setParkingRules(String parkingRules) {
        this.parkingRules = parkingRules;
    }

    public ReservationPolicyMode getReservationPolicyMode() { return reservationPolicyMode; }
    public void setReservationPolicyMode(ReservationPolicyMode reservationPolicyMode) {
        this.reservationPolicyMode = reservationPolicyMode;
    }

    public int getDefaultMaxDurationHours() { return defaultMaxDurationHours; }
    public void setDefaultMaxDurationHours(int defaultMaxDurationHours) {
        this.defaultMaxDurationHours = defaultMaxDurationHours;
    }

    public int getDefaultStartHour() { return defaultStartHour; }
    public void setDefaultStartHour(int defaultStartHour) {
        this.defaultStartHour = defaultStartHour;
    }

    public int getDefaultEndHour() { return defaultEndHour; }
    public void setDefaultEndHour(int defaultEndHour) {
        this.defaultEndHour = defaultEndHour;
    }

    public boolean isAllDayReservationAllowed() { return allDayReservationAllowed; }
    public void setAllDayReservationAllowed(boolean allDayReservationAllowed) {
        this.allDayReservationAllowed = allDayReservationAllowed;
    }

    public ReservationApprovalMode getReservationApprovalMode() { return reservationApprovalMode; }
    public void setReservationApprovalMode(ReservationApprovalMode reservationApprovalMode) {
        this.reservationApprovalMode = reservationApprovalMode;
    }

    public String getReservationRules() { return reservationRules; }
    public void setReservationRules(String reservationRules) {
        this.reservationRules = reservationRules;
    }
}
