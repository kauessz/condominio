package com.example.condo.repo;

import com.example.condo.entity.ParkingSpotAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ParkingSpotAssignmentRepository extends JpaRepository<ParkingSpotAssignment, Long> {

    @Query("""
        select a from ParkingSpotAssignment a
        where a.tenantId = :tenantId and a.condominiumId = :condoId
          and a.unitId = :unitId and a.status = 'ACTIVE'
          and a.validUntil >= :today
        order by a.validFrom desc
    """)
    Optional<ParkingSpotAssignment> findActiveAssignmentForUnit(
        @Param("tenantId") String tenantId,
        @Param("condoId") Long condoId,
        @Param("unitId") Long unitId,
        @Param("today") LocalDate today
    );

    @Query("""
        select a from ParkingSpotAssignment a
        where a.tenantId = :tenantId
          and a.status = 'ACTIVE'
        order by a.validFrom desc
    """)
    List<ParkingSpotAssignment> findAllActiveByTenant(@Param("tenantId") String tenantId);

    @Query("""
        select a from ParkingSpotAssignment a
        where a.tenantId = :tenantId and a.condominiumId = :condoId
          and a.status = 'ACTIVE'
        order by a.validFrom desc
    """)
    List<ParkingSpotAssignment> findAllActiveForCondo(@Param("tenantId") String tenantId,
                                                       @Param("condoId") Long condoId);

    List<ParkingSpotAssignment> findByDrawId(Long drawId);
}
