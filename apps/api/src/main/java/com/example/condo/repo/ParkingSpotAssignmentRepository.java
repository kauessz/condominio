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

    List<ParkingSpotAssignment> findByTenantIdAndCondominiumIdAndStatus(String tenantId,
                                                                        Long condominiumId,
                                                                        ParkingSpotAssignment.Status status);

    Optional<ParkingSpotAssignment> findByTenantIdAndId(String tenantId, Long id);

    List<ParkingSpotAssignment> findByDrawId(Long drawId);

    @Query("""
        select (count(a) > 0) from ParkingSpotAssignment a
        where a.tenantId = :tenantId
          and a.condominiumId = :condoId
          and a.spotId = :spotId
          and a.status = 'ACTIVE'
          and (:ignoreId is null or a.id <> :ignoreId)
          and a.validFrom <= :validUntil
          and a.validUntil >= :validFrom
    """)
    boolean existsActiveConflictForSpot(@Param("tenantId") String tenantId,
                                        @Param("condoId") Long condoId,
                                        @Param("spotId") Long spotId,
                                        @Param("validFrom") LocalDate validFrom,
                                        @Param("validUntil") LocalDate validUntil,
                                        @Param("ignoreId") Long ignoreId);

    @Query("""
        select (count(a) > 0) from ParkingSpotAssignment a
        where a.tenantId = :tenantId
          and a.condominiumId = :condoId
          and a.unitId = :unitId
          and a.status = 'ACTIVE'
          and (:ignoreId is null or a.id <> :ignoreId)
          and a.validFrom <= :validUntil
          and a.validUntil >= :validFrom
    """)
    boolean existsActiveConflictForUnit(@Param("tenantId") String tenantId,
                                        @Param("condoId") Long condoId,
                                        @Param("unitId") Long unitId,
                                        @Param("validFrom") LocalDate validFrom,
                                        @Param("validUntil") LocalDate validUntil,
                                        @Param("ignoreId") Long ignoreId);
}
