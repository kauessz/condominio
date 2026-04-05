package com.example.condo.dto.workorder;

import java.time.Instant;

public record WorkOrderListItemResponse(
    Long id,
    Long condominiumId,
    String condominiumName,
    Long unitId,
    Long categoryId,
    String categoryName,
    Long subcategoryId,
    String subcategoryName,
    String title,
    String description,
    String status,
    String priority,
    Instant slaDeadline,
    Instant createdAt
) {
}
