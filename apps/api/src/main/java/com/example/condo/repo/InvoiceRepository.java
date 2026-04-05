package com.example.condo.repo;

import com.example.condo.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByTenantIdAndId(String tenantId, Long id);

    boolean existsByUnitIdAndReferenceMonth(Long unitId, String referenceMonth);

    boolean existsByUnitIdAndLaunchKey(Long unitId, String launchKey);

    @Query("""
        select i from Invoice i
        where i.tenantId = :tenantId and i.condominiumId = :condoId
          and (:unitId is null or i.unitId = :unitId)
          and (:status is null or i.status = :status)
        order by i.dueDate desc
    """)
    Page<Invoice> search(@Param("tenantId") String tenantId,
                          @Param("condoId") Long condoId,
                          @Param("unitId") Long unitId,
                          @Param("status") Invoice.Status status,
                          Pageable pageable);

    @Query("""
        select i from Invoice i
        where i.tenantId = :tenantId
          and (:unitId is null or i.unitId = :unitId)
          and (:status is null or i.status = :status)
        order by i.dueDate desc
    """)
    Page<Invoice> searchByTenant(@Param("tenantId") String tenantId,
                                 @Param("unitId") Long unitId,
                                 @Param("status") Invoice.Status status,
                                 Pageable pageable);

    /** Para o job: marcar como OVERDUE invoices vencidas e ainda PENDING */
    @Query("select i from Invoice i where i.status = 'PENDING' and i.dueDate < :today")
    List<Invoice> findOverdue(@Param("today") LocalDate today);

    @Query("""
        select count(i), sum(i.amount),
               sum(case when i.status = 'PAID' then i.paidAmount else 0 end),
               sum(case when i.status = 'PENDING' then i.amount else 0 end),
               sum(case when i.status = 'OVERDUE' then i.amount else 0 end)
        from Invoice i
        where i.tenantId = :tenantId
          and (:condoId is null or i.condominiumId = :condoId)
          and (:unitId is null or i.unitId = :unitId)
          and (:status is null or i.status = :status)
    """)
    Object[] summary(@Param("tenantId") String tenantId,
                     @Param("condoId") Long condoId,
                     @Param("unitId") Long unitId,
                     @Param("status") Invoice.Status status);
}
