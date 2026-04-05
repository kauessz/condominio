package com.example.condo.repo;

import com.example.condo.entity.Reservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByTenantIdAndId(String tenantId, Long id);

    @Query("""
        select r from Reservation r
        where r.tenantId = :tenantId
          and r.condominiumId = :condoId
          and (:areaId is null or r.commonAreaId = :areaId)
          and (:unitId is null or r.unitId = :unitId)
          and (:status is null or r.status = :status)
        order by r.startDatetime desc
    """)
    Page<Reservation> search(@Param("tenantId") String tenantId,
                              @Param("condoId") Long condoId,
                              @Param("areaId") Long areaId,
                              @Param("unitId") Long unitId,
                              @Param("status") Reservation.Status status,
                              Pageable pageable);

    @Query("""
        select r from Reservation r
        where r.tenantId = :tenantId
          and (:areaId is null or r.commonAreaId = :areaId)
          and (:unitId is null or r.unitId = :unitId)
          and (:status is null or r.status = :status)
        order by r.startDatetime desc
    """)
    Page<Reservation> searchAllCondos(@Param("tenantId") String tenantId,
                                      @Param("areaId") Long areaId,
                                      @Param("unitId") Long unitId,
                                      @Param("status") Reservation.Status status,
                                      Pageable pageable);

    /** Verifica conflito de horário para uma área (excluindo canceladas/rejeitadas) */
    @Query("""
        select r from Reservation r
        where r.commonAreaId = :areaId
          and r.status not in ('CANCELLED', 'REJECTED', 'COMPLETED')
          and r.startDatetime < :endDt
          and r.endDatetime > :startDt
          and (:excludeId is null or r.id <> :excludeId)
    """)
    List<Reservation> findConflicts(@Param("areaId") Long areaId,
                                     @Param("startDt") Instant startDt,
                                     @Param("endDt") Instant endDt,
                                     @Param("excludeId") Long excludeId);

    /** Para o job de completar reservas passadas */
    @Query("select r from Reservation r where r.status = 'APPROVED' and r.endDatetime < :now")
    List<Reservation> findApprovedAndPast(@Param("now") Instant now);
}
