package com.example.condo.dto.assembly;

import java.util.List;

public record AssemblyCreateAgendaItemRequest(
    String title,
    String description,
    Boolean requiresVote,
    Integer sortOrder,
    String itemType,
    String officeName,
    List<Long> candidateUserIds
) {
}
