package com.example.condo.dto.financial;

import java.math.BigDecimal;

public record FinancialSummaryResponse(
    long totalInvoices,
    BigDecimal totalAmount,
    BigDecimal paidAmount,
    BigDecimal pendingAmount,
    BigDecimal overdueAmount,
    double delinquencyPct
) {
}
