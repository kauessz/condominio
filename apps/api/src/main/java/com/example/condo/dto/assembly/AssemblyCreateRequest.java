package com.example.condo.dto.assembly;

import java.time.Instant;
import java.util.List;

public record AssemblyCreateRequest(
    Long condominiumId,
    String title,
    String description,
    Instant scheduledAt,
    String location,
    List<AssemblyCreateAgendaItemRequest> agendaItems
) {
}
