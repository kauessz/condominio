package com.example.condo.service;

import com.example.condo.entity.CommonArea;
import com.example.condo.entity.Condominium;
import com.example.condo.entity.Reservation;
import com.example.condo.repo.CommonAreaRepository;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.ReservationRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private CommonAreaRepository areaRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private CondominiumRepository condominiumRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        TenantContext.set("tenant-a");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        UserContext.clear();
    }

    @Test
    void superuserShouldListReservationsAcrossAllCondominiumsWhenNoCondoFilterIsProvided() {
        UserContext.set(new UserContext.Data("SUPERUSER", null, null, 1L));
        when(reservationRepository.searchAllCondos(eq("tenant-a"), eq(null), eq(null), eq(null), any()))
            .thenReturn(new PageImpl<>(List.of(new Reservation())));

        var result = reservationService.listReservations(null, null, null, null, PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        verify(reservationRepository).searchAllCondos(eq("tenant-a"), eq(null), eq(null), eq(null), any());
        verify(reservationRepository, never()).search(eq("tenant-a"), eq(1L), eq(null), eq(null), eq(null), any());
    }

    @Test
    void sindicoShouldCreateReservationUsingOwnUnitWhenUnitIsNotProvided() {
        UserContext.set(new UserContext.Data("SINDICO", 10L, 101L, 2L));

        CommonArea area = new CommonArea();
        area.setId(5L);
        area.setTenantId("tenant-a");
        area.setCondominiumId(10L);
        area.setMaxHoursPerReservation(4);
        area.setRequiresApproval(false);

        Condominium condominium = new Condominium();
        condominium.setId(10L);
        condominium.setTenantId("tenant-a");
        condominium.setDefaultMaxDurationHours(4);
        condominium.setDefaultStartHour(8);
        condominium.setDefaultEndHour(22);
        condominium.setAllDayReservationAllowed(false);
        condominium.setReservationApprovalMode(Condominium.ReservationApprovalMode.AUTOMATIC);
        condominium.setReservationPolicyMode(Condominium.ReservationPolicyMode.FLEXIBLE_INTERVAL);

        when(areaRepository.findByTenantIdAndId("tenant-a", 5L)).thenReturn(Optional.of(area));
        when(condominiumRepository.findByTenantIdAndId("tenant-a", 10L)).thenReturn(Optional.of(condominium));
        when(unitRepository.existsByTenantIdAndIdAndCondominiumId("tenant-a", 101L, 10L)).thenReturn(true);
        when(reservationRepository.findConflicts(eq(5L), any(), any(), eq(null))).thenReturn(List.of());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            reservation.setId(99L);
            return reservation;
        });
        Instant start = LocalDateTime.now()
            .plusDays(1)
            .withHour(10)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .atZone(ZoneId.systemDefault())
            .toInstant();
        Instant end = start.plus(2, ChronoUnit.HOURS);

        Reservation created = reservationService.createReservation(
            null,
            5L,
            null,
            start,
            end,
            "Reserva do síndico",
            null
        );

        assertEquals(101L, created.getUnitId());
        assertEquals(Reservation.Status.APPROVED, created.getStatus());
    }
}
