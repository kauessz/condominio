package com.example.condo.dto.parking;

import java.time.LocalDate;

public record ParkingAssignmentResponse(
    Long id,
    Long condominiumId,
    Long spotId,
    String spotCode,
    String spotDescription,
    Long unitId,
    String unitLabel,
    String residentName,
    Long drawId,
    String drawName,
    LocalDate validFrom,
    LocalDate validUntil,
    String status
) {
}
