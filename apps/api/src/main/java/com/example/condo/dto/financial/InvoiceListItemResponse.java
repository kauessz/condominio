package com.example.condo.dto.financial;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InvoiceListItemResponse(
    Long id,
    Long condominiumId,
    String condominiumName,
    Long unitId,
    String unitLabel,
    String residentName,
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
    String externalProvider,
    String externalChargeId,
    String externalStatus,
    String billingType,
    String boletoUrl,
    String invoiceUrl,
    String pixCopyPaste,
    String pixQrCode,
    Instant pixExpiresAt,
    Instant lastWebhookAt,
    Instant lastNotificationAt,
    String lastNotificationType
) {
}
