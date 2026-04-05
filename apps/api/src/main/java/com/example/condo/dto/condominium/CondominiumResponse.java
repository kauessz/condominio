package com.example.condo.dto.condominium;

import com.example.condo.entity.Condominium;

import java.time.LocalDateTime;

/**
 * DTO para resposta de condomínio (detalhes).
 */
public record CondominiumResponse(
    Long id,
    String name,
    String cnpj,
    Boolean active,
    LocalDateTime createdAt,
    Long unitCount,
    Long residentCount,
    Boolean allowSyndicApproveVisitor,
    Boolean residentApprovalRequired,
    Boolean adminOverrideAllowed,
    Boolean portariaCanAutoApprove,
    String parkingPolicyMode,
    String parkingDrawFrequency,
    Integer drawIntervalMonths,
    Boolean allowManualAssignments,
    Boolean allowResidentRegistration,
    Integer maxVehiclesPerUnit,
    String parkingRules,
    String reservationPolicyMode,
    Integer defaultMaxDurationHours,
    Integer defaultStartHour,
    Integer defaultEndHour,
    Boolean allDayReservationAllowed,
    String reservationApprovalMode,
    String reservationRules
) {

    /**
     * Converte entidade para DTO (sem contadores).
     */
    public static CondominiumResponse from(Condominium condominium) {
        return new CondominiumResponse(
            condominium.getId(),
            condominium.getName(),
            condominium.getCnpj(),
            condominium.isActive(),
            condominium.getCreatedAt(),
            null,
            null,
            condominium.isAllowSyndicApproveVisitor(),
            condominium.isResidentApprovalRequired(),
            condominium.isAdminOverrideAllowed(),
            condominium.isPortariaCanAutoApprove(),
            condominium.getParkingPolicyMode() != null ? condominium.getParkingPolicyMode().name() : null,
            condominium.getParkingDrawFrequency() != null ? condominium.getParkingDrawFrequency().name() : null,
            condominium.getDrawIntervalMonths(),
            condominium.isAllowManualAssignments(),
            condominium.isAllowResidentRegistration(),
            condominium.getMaxVehiclesPerUnit(),
            condominium.getParkingRules(),
            condominium.getReservationPolicyMode() != null ? condominium.getReservationPolicyMode().name() : null,
            condominium.getDefaultMaxDurationHours(),
            condominium.getDefaultStartHour(),
            condominium.getDefaultEndHour(),
            condominium.isAllDayReservationAllowed(),
            condominium.getReservationApprovalMode() != null ? condominium.getReservationApprovalMode().name() : null,
            condominium.getReservationRules()
        );
    }

    /**
     * Converte entidade para DTO com contadores.
     */
    public static CondominiumResponse withCounts(
        Condominium condominium,
        Long unitCount,
        Long residentCount
    ) {
        return new CondominiumResponse(
            condominium.getId(),
            condominium.getName(),
            condominium.getCnpj(),
            condominium.isActive(),
            condominium.getCreatedAt(),
            unitCount,
            residentCount,
            condominium.isAllowSyndicApproveVisitor(),
            condominium.isResidentApprovalRequired(),
            condominium.isAdminOverrideAllowed(),
            condominium.isPortariaCanAutoApprove(),
            condominium.getParkingPolicyMode() != null ? condominium.getParkingPolicyMode().name() : null,
            condominium.getParkingDrawFrequency() != null ? condominium.getParkingDrawFrequency().name() : null,
            condominium.getDrawIntervalMonths(),
            condominium.isAllowManualAssignments(),
            condominium.isAllowResidentRegistration(),
            condominium.getMaxVehiclesPerUnit(),
            condominium.getParkingRules(),
            condominium.getReservationPolicyMode() != null ? condominium.getReservationPolicyMode().name() : null,
            condominium.getDefaultMaxDurationHours(),
            condominium.getDefaultStartHour(),
            condominium.getDefaultEndHour(),
            condominium.isAllDayReservationAllowed(),
            condominium.getReservationApprovalMode() != null ? condominium.getReservationApprovalMode().name() : null,
            condominium.getReservationRules()
        );
    }
}
