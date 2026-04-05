package com.example.condo.repo;

import com.example.condo.entity.ParkingSpot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, Long> {

    Optional<ParkingSpot> findByTenantIdAndId(String tenantId, Long id);

    @Query("select s from ParkingSpot s where s.tenantId = :tenantId and s.active = true order by s.code")
    List<ParkingSpot> findAllActiveByTenant(@Param("tenantId") String tenantId);

    @Query("select s from ParkingSpot s where s.tenantId = :tenantId and s.condominiumId = :condoId and s.active = true order by s.code")
    List<ParkingSpot> findAllActive(@Param("tenantId") String tenantId, @Param("condoId") Long condoId);

    @Query("select s from ParkingSpot s where s.tenantId = :tenantId order by s.code")
    Page<ParkingSpot> findAllByTenant(@Param("tenantId") String tenantId, Pageable pageable);

    @Query("select s from ParkingSpot s where s.tenantId = :tenantId and s.condominiumId = :condoId order by s.code")
    Page<ParkingSpot> findAll(@Param("tenantId") String tenantId, @Param("condoId") Long condoId, Pageable pageable);
}
