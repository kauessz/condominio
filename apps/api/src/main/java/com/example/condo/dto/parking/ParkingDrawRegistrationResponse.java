package com.example.condo.dto.parking;

import java.time.Instant;

public record ParkingDrawRegistrationResponse(
    Long id,
    Long drawId,
    Long condominiumId,
    Long unitId,
    String unitLabel,
    Long residentId,
    String residentName,
    Instant registeredAt,
    boolean hasActiveAssignment
) {
}
