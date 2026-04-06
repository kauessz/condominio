package com.example.condo.dto.parking;

import java.time.LocalDate;

public record ParkingAssignmentRequest(
    Long condominiumId,
    Long spotId,
    Long unitId,
    LocalDate validFrom,
    LocalDate validUntil
) {
}
