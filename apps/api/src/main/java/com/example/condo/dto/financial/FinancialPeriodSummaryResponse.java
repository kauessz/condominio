package com.example.condo.dto.financial;

import java.math.BigDecimal;

public record FinancialPeriodSummaryResponse(
    String referenceMonth,
    long totalInvoices,
    BigDecimal totalAmount,
    BigDecimal paidAmount,
    BigDecimal overdueAmount
) {
}
