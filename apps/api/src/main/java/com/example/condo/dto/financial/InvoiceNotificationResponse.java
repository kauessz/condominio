package com.example.condo.dto.financial;

import java.time.Instant;

public record InvoiceNotificationResponse(
    Long id,
    String type,
    String channel,
    String status,
    String recipientName,
    String recipientEmail,
    String message,
    Instant createdAt,
    Instant sentAt
) {
}
