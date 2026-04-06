package com.example.condo.dto.assembly;

import java.util.List;

public record AssemblyAgendaItemResponse(
    Long id,
    Long assemblyId,
    String title,
    String description,
    boolean requiresVote,
    int sortOrder,
    String itemType,
    String officeName,
    String resolutionStatus,
    Long winningOptionId,
    Long appliedUserId,
    List<AssemblyAgendaOptionResponse> options
) {
}
