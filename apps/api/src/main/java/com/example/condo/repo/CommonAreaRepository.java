package com.example.condo.repo;

import com.example.condo.entity.CommonArea;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CommonAreaRepository extends JpaRepository<CommonArea, Long> {

    @Query("select a from CommonArea a where a.tenantId = :tenantId and a.active = true order by a.name")
    Page<CommonArea> findAllActiveByTenant(@Param("tenantId") String tenantId, Pageable pageable);

    @Query("select a from CommonArea a where a.tenantId = :tenantId and a.condominiumId = :condoId and a.active = true order by a.name")
    Page<CommonArea> findAllActive(@Param("tenantId") String tenantId,
                                   @Param("condoId") Long condoId,
                                   Pageable pageable);

    Optional<CommonArea> findByTenantIdAndId(String tenantId, Long id);
}
