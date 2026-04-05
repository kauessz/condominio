package com.example.condo.repo;

import com.example.condo.entity.WorkOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    Optional<WorkOrder> findByTenantIdAndId(String tenantId, Long id);

    @Query("""
        select o from WorkOrder o
        where o.tenantId = :tenantId
          and o.condominiumId = :condoId
          and (:status is null or o.status = :status)
          and (:unitId is null or o.unitId = :unitId)
          and (:categoryId is null or o.categoryId = :categoryId)
        order by o.createdAt desc
    """)
    Page<WorkOrder> search(@Param("tenantId") String tenantId,
                            @Param("condoId") Long condoId,
                            @Param("status") WorkOrder.Status status,
                            @Param("unitId") Long unitId,
                            @Param("categoryId") Long categoryId,
                            Pageable pageable);

    @Query("""
        select o from WorkOrder o
        where o.tenantId = :tenantId
          and (:status is null or o.status = :status)
          and (:unitId is null or o.unitId = :unitId)
          and (:categoryId is null or o.categoryId = :categoryId)
        order by o.createdAt desc
    """)
    Page<WorkOrder> searchAllCondos(@Param("tenantId") String tenantId,
                                    @Param("status") WorkOrder.Status status,
                                    @Param("unitId") Long unitId,
                                    @Param("categoryId") Long categoryId,
                                    Pageable pageable);
}
