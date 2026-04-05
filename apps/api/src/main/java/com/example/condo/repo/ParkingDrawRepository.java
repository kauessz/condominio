package com.example.condo.repo;

import com.example.condo.entity.ParkingDraw;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ParkingDrawRepository extends JpaRepository<ParkingDraw, Long> {

    Optional<ParkingDraw> findByTenantIdAndId(String tenantId, Long id);

    @Query("select d from ParkingDraw d where d.tenantId = :tenantId order by d.createdAt desc")
    Page<ParkingDraw> findAllByTenant(@Param("tenantId") String tenantId, Pageable pageable);

    @Query("select d from ParkingDraw d where d.tenantId = :tenantId and d.condominiumId = :condoId order by d.createdAt desc")
    Page<ParkingDraw> findAll(@Param("tenantId") String tenantId, @Param("condoId") Long condoId, Pageable pageable);
}
