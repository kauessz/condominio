package com.example.condo.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "visitor")
public class Visitor {

    public enum Status {
        PENDING, // aguardando aprovação ou apenas lançado
        APPROVED, // aprovado (ex.: pelo morador)
        REJECTED, // reprovado
        CHECKED_OUT // já saiu
    }

    public enum Type {
        VISITOR, // visitante comum
        DELIVERY, // entregador
        SERVICE // prestador de serviço
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "condominium_id", nullable = false)
    private Long condominiumId;

    @Column(name = "unit_id")
    private Long unitId; // opcional

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "document", length = 100)
    private String document;

    @Column(name = "plate", length = 20)
    private String plate;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "email", length = 200)
    private String email;

    @Column(name = "note", length = 2000)
    private String note;

    // IMPORTANTE: default now() para acompanhar a migração
    @Column(name = "check_in_at", nullable = false)
    private Instant checkInAt;

    @Column(name = "check_out_at")
    private Instant checkOutAt;

    @Column(name = "expected_in_at")
    private Instant expectedInAt;

    @Column(name = "expected_out_at")
    private Instant expectedOutAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private Type type = Type.VISITOR;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // ===== Getters/Setters =====

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

    public Long getCondominiumId() {
        return condominiumId;
    }

    public void setCondominiumId(Long condominiumId) {
        this.condominiumId = condominiumId;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDocument() {
        return document;
    }

    public void setDocument(String document) {
        this.document = document;
    }

    public String getPlate() {
        return plate;
    }

    public void setPlate(String plate) {
        this.plate = plate;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCheckInAt() {
        return checkInAt;
    }

    public void setCheckInAt(Instant checkInAt) {
        this.checkInAt = checkInAt;
    }

    public Instant getCheckOutAt() {
        return checkOutAt;
    }

    public void setCheckOutAt(Instant checkOutAt) {
        this.checkOutAt = checkOutAt;
    }

    public Instant getExpectedInAt() {
        return expectedInAt;
    }

    public void setExpectedInAt(Instant expectedInAt) {
        this.expectedInAt = expectedInAt;
    }

    public Instant getExpectedOutAt() {
        return expectedOutAt;
    }

    public void setExpectedOutAt(Instant expectedOutAt) {
        this.expectedOutAt = expectedOutAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}