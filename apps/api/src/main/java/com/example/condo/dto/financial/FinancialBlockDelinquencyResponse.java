package com.example.condo.dto.financial;

import java.math.BigDecimal;

public record FinancialBlockDelinquencyResponse(
    String block,
    long overdueInvoices,
    BigDecimal overdueAmount,
    BigDecimal openAmount
) {
}
