package com.example.condo.dto.assembly;

import java.time.Instant;

public record AssemblyListItemResponse(
    Long id,
    Long condominiumId,
    String condominiumName,
    String title,
    String description,
    String status,
    Instant scheduledAt,
    String location,
    Long agendaItemCount,
    Boolean canVote,
    Boolean alreadyVoted,
    String voteStatus
) {
    public AssemblyListItemResponse(
        Long id,
        Long condominiumId,
        String condominiumName,
        String title,
        String description,
        String status,
        Instant scheduledAt,
        String location,
        Long agendaItemCount
    ) {
        this(id, condominiumId, condominiumName, title, description, status, scheduledAt, location, agendaItemCount, null, null, null);
    }
}
