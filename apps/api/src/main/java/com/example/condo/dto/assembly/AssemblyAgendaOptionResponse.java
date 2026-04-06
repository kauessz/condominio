package com.example.condo.dto.assembly;

public record AssemblyAgendaOptionResponse(
    Long id,
    Long candidateUserId,
    String candidateName,
    String candidateUnitLabel,
    int sortOrder
) {
}
