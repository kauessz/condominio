package com.example.condo.dto.financial;

import java.time.Instant;
import java.util.Map;

public record InvoiceEventResponse(
    Long id,
    String type,
    String source,
    String description,
    Instant createdAt,
    Map<String, Object> payload
) {
}
