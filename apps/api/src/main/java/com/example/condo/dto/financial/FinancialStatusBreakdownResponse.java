package com.example.condo.dto.financial;

import java.math.BigDecimal;

public record FinancialStatusBreakdownResponse(
    String status,
    long totalInvoices,
    BigDecimal totalAmount
) {
}
