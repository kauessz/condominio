package com.example.condo.dto.condominium;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para criar um novo condomínio.
 */
public record CreateCondominiumRequest(

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 3, max = 200, message = "Nome deve ter entre 3 e 200 caracteres")
    String name,

    @Pattern(
        regexp = "^\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}$",
        message = "CNPJ deve estar no formato XX.XXX.XXX/XXXX-XX"
    )
    String cnpj,

    Boolean active,

    Boolean allowSyndicApproveVisitor,
    Boolean residentApprovalRequired,
    Boolean adminOverrideAllowed,
    Boolean portariaCanAutoApprove,

    String parkingPolicyMode,
    String parkingDrawFrequency,

    @Min(value = 1, message = "drawIntervalMonths deve ser maior que zero")
    Integer drawIntervalMonths,

    Boolean allowManualAssignments,
    Boolean allowResidentRegistration,

    @Min(value = 1, message = "maxVehiclesPerUnit deve ser maior que zero")
    Integer maxVehiclesPerUnit,

    String parkingRules,

    String reservationPolicyMode,

    @Min(value = 1, message = "defaultMaxDurationHours deve ser maior que zero")
    Integer defaultMaxDurationHours,

    @Min(value = 0, message = "defaultStartHour deve ser maior ou igual a zero")
    Integer defaultStartHour,

    @Min(value = 0, message = "defaultEndHour deve ser maior ou igual a zero")
    Integer defaultEndHour,

    Boolean allDayReservationAllowed,

    String reservationApprovalMode,

    String reservationRules
) {
}
