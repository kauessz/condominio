package com.example.condo.dto.assembly;

public record AssemblyElectionCandidateResponse(
    Long userId,
    Long residentId,
    Long condominiumId,
    Long unitId,
    String name,
    String role,
    String unitLabel
) {
}
