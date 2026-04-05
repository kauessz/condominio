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
    String referenceMonth,
    String chargeType,
    String title,
    String description,
    BigDecimal amount,
    LocalDate dueDate,
    String status,
    Instant paidAt,
    BigDecimal paidAmount,
    String paymentMethod
) {
}
