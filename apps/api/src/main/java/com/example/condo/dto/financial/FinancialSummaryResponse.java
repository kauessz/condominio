package com.example.condo.dto.financial;

import java.math.BigDecimal;
import java.util.List;

public record FinancialSummaryResponse(
    long totalInvoices,
    BigDecimal totalAmount,
    BigDecimal paidAmount,
    BigDecimal pendingAmount,
    BigDecimal overdueAmount,
    double delinquencyPct,
    List<FinancialStatusBreakdownResponse> totalsByStatus,
    List<FinancialBlockDelinquencyResponse> delinquencyByBlock,
    List<FinancialPeriodSummaryResponse> totalsByReferenceMonth
) {
    public FinancialSummaryResponse(
        long totalInvoices,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal pendingAmount,
        BigDecimal overdueAmount,
        double delinquencyPct
    ) {
        this(
            totalInvoices,
            totalAmount,
            paidAmount,
            pendingAmount,
            overdueAmount,
            delinquencyPct,
            List.of(),
            List.of(),
            List.of()
        );
    }
}
