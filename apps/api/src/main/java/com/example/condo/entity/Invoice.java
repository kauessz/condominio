package com.example.condo.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "invoice")
public class Invoice {

    public enum Status { DRAFT, PENDING, EXTERNAL_CREATED, AWAITING_PAYMENT, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED, FAILED, WAIVED }
    public enum PaymentMethod { PIX, BOLETO, TRANSFER, CASH, OTHER }
    public enum ChargeType { CONDOMINIO, REFORMA, EXTRA, FUNDO_RESERVA, MULTA, OUTROS }
    public enum Provider { ASAAS, MANUAL }
    public enum BillingType { UNDEFINED, PIX, BOLETO, PIX_AND_BOLETO }
    public enum ApportionmentMode { NONE, EQUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "condominium_id", nullable = false)
    private Long condominiumId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "reference_month", nullable = false)
    private String referenceMonth; // "2025-01"

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_type", nullable = false)
    private ChargeType chargeType = ChargeType.CONDOMINIO;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "launch_key", nullable = false)
    private String launchKey;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_amount")
    private BigDecimal paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "payment_notes")
    private String paymentNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "external_provider")
    private Provider externalProvider;

    @Column(name = "external_charge_id")
    private String externalChargeId;

    @Column(name = "external_invoice_number")
    private String externalInvoiceNumber;

    @Column(name = "external_customer_id")
    private String externalCustomerId;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "external_status")
    private String externalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type")
    private BillingType billingType;

    @Column(name = "pix_qr_code")
    private String pixQrCode;

    @Column(name = "pix_copy_paste")
    private String pixCopyPaste;

    @Column(name = "boleto_url")
    private String boletoUrl;

    @Column(name = "invoice_url")
    private String invoiceUrl;

    @Column(name = "pix_expires_at")
    private Instant pixExpiresAt;

    @Column(name = "last_webhook_at")
    private Instant lastWebhookAt;

    @Column(name = "last_notification_at")
    private Instant lastNotificationAt;

    @Column(name = "last_notification_type")
    private String lastNotificationType;

    @Column(name = "external_created_at")
    private Instant externalCreatedAt;

    @Column(name = "external_updated_at")
    private Instant externalUpdatedAt;

    @Column(name = "external_last_error")
    private String externalLastError;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "apportionment_group")
    private String apportionmentGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "apportionment_mode")
    private ApportionmentMode apportionmentMode = ApportionmentMode.NONE;

    @Column(name = "external_last_event_id")
    private String externalLastEventId;

    @Column(name = "payment_received_at")
    private Instant paymentReceivedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "registered_by")
    private Long registeredBy;

    @Column(name = "created_at")
    private Instant createdAt;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Long getCondominiumId() { return condominiumId; }
    public void setCondominiumId(Long condominiumId) { this.condominiumId = condominiumId; }

    public Long getUnitId() { return unitId; }
    public void setUnitId(Long unitId) { this.unitId = unitId; }

    public String getReferenceMonth() { return referenceMonth; }
    public void setReferenceMonth(String referenceMonth) { this.referenceMonth = referenceMonth; }

    public ChargeType getChargeType() { return chargeType; }
    public void setChargeType(ChargeType chargeType) { this.chargeType = chargeType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLaunchKey() { return launchKey; }
    public void setLaunchKey(String launchKey) { this.launchKey = launchKey; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPaymentNotes() { return paymentNotes; }
    public void setPaymentNotes(String paymentNotes) { this.paymentNotes = paymentNotes; }

    public Provider getExternalProvider() { return externalProvider; }
    public void setExternalProvider(Provider externalProvider) { this.externalProvider = externalProvider; }

    public String getExternalChargeId() { return externalChargeId; }
    public void setExternalChargeId(String externalChargeId) { this.externalChargeId = externalChargeId; }

    public String getExternalInvoiceNumber() { return externalInvoiceNumber; }
    public void setExternalInvoiceNumber(String externalInvoiceNumber) { this.externalInvoiceNumber = externalInvoiceNumber; }

    public String getExternalCustomerId() { return externalCustomerId; }
    public void setExternalCustomerId(String externalCustomerId) { this.externalCustomerId = externalCustomerId; }

    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }

    public String getExternalStatus() { return externalStatus; }
    public void setExternalStatus(String externalStatus) { this.externalStatus = externalStatus; }

    public BillingType getBillingType() { return billingType; }
    public void setBillingType(BillingType billingType) { this.billingType = billingType; }

    public String getPixQrCode() { return pixQrCode; }
    public void setPixQrCode(String pixQrCode) { this.pixQrCode = pixQrCode; }

    public String getPixCopyPaste() { return pixCopyPaste; }
    public void setPixCopyPaste(String pixCopyPaste) { this.pixCopyPaste = pixCopyPaste; }

    public String getBoletoUrl() { return boletoUrl; }
    public void setBoletoUrl(String boletoUrl) { this.boletoUrl = boletoUrl; }

    public String getInvoiceUrl() { return invoiceUrl; }
    public void setInvoiceUrl(String invoiceUrl) { this.invoiceUrl = invoiceUrl; }

    public Instant getPixExpiresAt() { return pixExpiresAt; }
    public void setPixExpiresAt(Instant pixExpiresAt) { this.pixExpiresAt = pixExpiresAt; }

    public Instant getLastWebhookAt() { return lastWebhookAt; }
    public void setLastWebhookAt(Instant lastWebhookAt) { this.lastWebhookAt = lastWebhookAt; }

    public Instant getLastNotificationAt() { return lastNotificationAt; }
    public void setLastNotificationAt(Instant lastNotificationAt) { this.lastNotificationAt = lastNotificationAt; }

    public String getLastNotificationType() { return lastNotificationType; }
    public void setLastNotificationType(String lastNotificationType) { this.lastNotificationType = lastNotificationType; }

    public Instant getExternalCreatedAt() { return externalCreatedAt; }
    public void setExternalCreatedAt(Instant externalCreatedAt) { this.externalCreatedAt = externalCreatedAt; }

    public Instant getExternalUpdatedAt() { return externalUpdatedAt; }
    public void setExternalUpdatedAt(Instant externalUpdatedAt) { this.externalUpdatedAt = externalUpdatedAt; }

    public String getExternalLastError() { return externalLastError; }
    public void setExternalLastError(String externalLastError) { this.externalLastError = externalLastError; }

    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }

    public Instant getFailedAt() { return failedAt; }
    public void setFailedAt(Instant failedAt) { this.failedAt = failedAt; }

    public String getApportionmentGroup() { return apportionmentGroup; }
    public void setApportionmentGroup(String apportionmentGroup) { this.apportionmentGroup = apportionmentGroup; }

    public ApportionmentMode getApportionmentMode() { return apportionmentMode; }
    public void setApportionmentMode(ApportionmentMode apportionmentMode) { this.apportionmentMode = apportionmentMode; }

    public String getExternalLastEventId() { return externalLastEventId; }
    public void setExternalLastEventId(String externalLastEventId) { this.externalLastEventId = externalLastEventId; }

    public Instant getPaymentReceivedAt() { return paymentReceivedAt; }
    public void setPaymentReceivedAt(Instant paymentReceivedAt) { this.paymentReceivedAt = paymentReceivedAt; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Long getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(Long registeredBy) { this.registeredBy = registeredBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
