package com.example.condo.dto.financial;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record InvoiceDetailResponse(
    Long id,
    Long condominiumId,
    String condominiumName,
    Long unitId,
    String unitLabel,
    String referenceMonth,
    String chargeType,
    String title,
    String description,
    BigDecimal amount,
    LocalDate dueDate,
    String status,
    Instant paidAt,
    BigDecimal paidAmount,
    String paymentMethod,
    String paymentNotes,
    String externalProvider,
    String externalChargeId,
    String externalInvoiceNumber,
    String externalStatus,
    String billingType,
    String boletoUrl,
    String invoiceUrl,
    String pixQrCode,
    String pixCopyPaste,
    Instant externalCreatedAt,
    Instant lastWebhookAt,
    String apportionmentMode,
    String apportionmentGroup,
    Long registeredBy,
    Instant createdAt,
    List<InvoiceEventResponse> events,
    List<InvoiceNotificationResponse> notifications
) {
}
