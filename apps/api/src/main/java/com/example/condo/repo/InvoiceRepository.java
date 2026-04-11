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

    boolean existsByTenantIdAndUnitIdAndLaunchKey(String tenantId, Long unitId, String launchKey);

    Optional<Invoice> findByTenantIdAndExternalChargeId(String tenantId, String externalChargeId);

    Optional<Invoice> findByTenantIdAndExternalReference(String tenantId, String externalReference);

    Optional<Invoice> findByExternalProviderAndExternalChargeId(Invoice.Provider externalProvider, String externalChargeId);

    Optional<Invoice> findTopByTenantIdAndUnitIdAndExternalProviderAndExternalCustomerIdIsNotNullOrderByIdDesc(
        String tenantId,
        Long unitId,
        Invoice.Provider externalProvider
    );

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

    /**
     * Busca avançada de invoices com filtros opcionais.
     *
     * <p>Usa native SQL para contornar bug do Hibernate 6 + PostgreSQL JDBC ≥ 42.3:
     * parâmetros JDBC sem tipo explícito são inferidos como {@code bytea} pelo driver,
     * causando erros como {@code lower(bytea) does not exist} ou
     * {@code could not determine data type of parameter $N}.</p>
     *
     * <p>Solução: todos os parâmetros String/enum nullable usam {@code CAST(:param AS tipo)}
     * para forçar o tipo correto no bind JDBC.</p>
     */
    @Query(
        nativeQuery = true,
        value = """
            SELECT i.*
            FROM   invoice i
            WHERE  i.tenant_id = :tenantId
              AND  (:condoId          IS NULL OR i.condominium_id  = :condoId)
              AND  (:unitId           IS NULL OR i.unit_id         = :unitId)
              AND  (:residentId       IS NULL OR EXISTS (
                       SELECT 1 FROM resident r
                       WHERE  r.tenant_id      = i.tenant_id
                         AND  r.condominium_id = i.condominium_id
                         AND  r.unit_id        = i.unit_id
                         AND  r.id             = :residentId
                   ))
              AND  (CAST(:status AS text)      IS NULL OR i.status      = CAST(:status AS text))
              AND  (CAST(:chargeType AS text)  IS NULL OR i.charge_type = CAST(:chargeType AS text))
              AND  (CAST(:referenceMonthFrom AS text) IS NULL OR i.reference_month >= CAST(:referenceMonthFrom AS text))
              AND  (CAST(:referenceMonthTo   AS text) IS NULL OR i.reference_month <= CAST(:referenceMonthTo   AS text))
              AND  (:dueDateFrom      IS NULL OR i.due_date >= :dueDateFrom)
              AND  (:dueDateTo        IS NULL OR i.due_date <= :dueDateTo)
              AND  (
                    CAST(:searchPattern AS text) IS NULL
                    OR lower(i.title)       LIKE lower(CAST(:searchPattern AS text))
                    OR lower(i.description) LIKE lower(CAST(:searchPattern AS text))
                    OR EXISTS (
                        SELECT 1 FROM unit u
                        WHERE  u.tenant_id = i.tenant_id
                          AND  u.id        = i.unit_id
                          AND  (
                                   lower(u.number) LIKE lower(CAST(:searchPattern AS text))
                                OR lower(u.block)  LIKE lower(CAST(:searchPattern AS text))
                                OR lower(u.code)   LIKE lower(CAST(:searchPattern AS text))
                               )
                    )
                    OR EXISTS (
                        SELECT 1 FROM resident r
                        WHERE  r.tenant_id      = i.tenant_id
                          AND  r.condominium_id = i.condominium_id
                          AND  r.unit_id        = i.unit_id
                          AND  (
                                   lower(r.name)  LIKE lower(CAST(:searchPattern AS text))
                                OR lower(r.email) LIKE lower(CAST(:searchPattern AS text))
                               )
                    )
                   )
            ORDER BY
              CASE WHEN CAST(:sortBy AS text) = 'createdAt'      AND CAST(:direction AS text) = 'ASC'  THEN i.created_at      END ASC,
              CASE WHEN CAST(:sortBy AS text) = 'createdAt'      AND CAST(:direction AS text) = 'DESC' THEN i.created_at      END DESC,
              CASE WHEN CAST(:sortBy AS text) = 'amount'         AND CAST(:direction AS text) = 'ASC'  THEN i.amount          END ASC,
              CASE WHEN CAST(:sortBy AS text) = 'amount'         AND CAST(:direction AS text) = 'DESC' THEN i.amount          END DESC,
              CASE WHEN CAST(:sortBy AS text) = 'status'         AND CAST(:direction AS text) = 'ASC'  THEN i.status          END ASC,
              CASE WHEN CAST(:sortBy AS text) = 'status'         AND CAST(:direction AS text) = 'DESC' THEN i.status          END DESC,
              CASE WHEN CAST(:sortBy AS text) = 'title'          AND CAST(:direction AS text) = 'ASC'  THEN i.title           END ASC,
              CASE WHEN CAST(:sortBy AS text) = 'title'          AND CAST(:direction AS text) = 'DESC' THEN i.title           END DESC,
              CASE WHEN CAST(:sortBy AS text) = 'referenceMonth' AND CAST(:direction AS text) = 'ASC'  THEN i.reference_month END ASC,
              CASE WHEN CAST(:sortBy AS text) = 'referenceMonth' AND CAST(:direction AS text) = 'DESC' THEN i.reference_month END DESC,
              CASE WHEN CAST(:sortBy AS text) = 'dueDate'        AND CAST(:direction AS text) = 'ASC'  THEN i.due_date        END ASC,
              CASE WHEN CAST(:sortBy AS text) = 'dueDate'        AND CAST(:direction AS text) = 'DESC' THEN i.due_date        END DESC,
              i.due_date DESC,
              i.id DESC
            """,
        countQuery = """
            SELECT COUNT(*)
            FROM   invoice i
            WHERE  i.tenant_id = :tenantId
              AND  (:condoId          IS NULL OR i.condominium_id  = :condoId)
              AND  (:unitId           IS NULL OR i.unit_id         = :unitId)
              AND  (:residentId       IS NULL OR EXISTS (
                       SELECT 1 FROM resident r
                       WHERE  r.tenant_id      = i.tenant_id
                         AND  r.condominium_id = i.condominium_id
                         AND  r.unit_id        = i.unit_id
                         AND  r.id             = :residentId
                   ))
              AND  (CAST(:status AS text)      IS NULL OR i.status      = CAST(:status AS text))
              AND  (CAST(:chargeType AS text)  IS NULL OR i.charge_type = CAST(:chargeType AS text))
              AND  (CAST(:referenceMonthFrom AS text) IS NULL OR i.reference_month >= CAST(:referenceMonthFrom AS text))
              AND  (CAST(:referenceMonthTo   AS text) IS NULL OR i.reference_month <= CAST(:referenceMonthTo   AS text))
              AND  (:dueDateFrom      IS NULL OR i.due_date >= :dueDateFrom)
              AND  (:dueDateTo        IS NULL OR i.due_date <= :dueDateTo)
              AND  (
                    CAST(:searchPattern AS text) IS NULL
                    OR lower(i.title)       LIKE lower(CAST(:searchPattern AS text))
                    OR lower(i.description) LIKE lower(CAST(:searchPattern AS text))
                    OR EXISTS (
                        SELECT 1 FROM unit u
                        WHERE  u.tenant_id = i.tenant_id
                          AND  u.id        = i.unit_id
                          AND  (
                                   lower(u.number) LIKE lower(CAST(:searchPattern AS text))
                                OR lower(u.block)  LIKE lower(CAST(:searchPattern AS text))
                                OR lower(u.code)   LIKE lower(CAST(:searchPattern AS text))
                               )
                    )
                    OR EXISTS (
                        SELECT 1 FROM resident r
                        WHERE  r.tenant_id      = i.tenant_id
                          AND  r.condominium_id = i.condominium_id
                          AND  r.unit_id        = i.unit_id
                          AND  (
                                   lower(r.name)  LIKE lower(CAST(:searchPattern AS text))
                                OR lower(r.email) LIKE lower(CAST(:searchPattern AS text))
                               )
                    )
                   )
            """
    )
    Page<Invoice> searchAdvanced(@Param("tenantId") String tenantId,
                                 @Param("condoId") Long condoId,
                                 @Param("unitId") Long unitId,
                                 @Param("residentId") Long residentId,
                                 @Param("status") String status,
                                 @Param("chargeType") String chargeType,
                                 @Param("referenceMonthFrom") String referenceMonthFrom,
                                 @Param("referenceMonthTo") String referenceMonthTo,
                                 @Param("dueDateFrom") LocalDate dueDateFrom,
                                 @Param("dueDateTo") LocalDate dueDateTo,
                                 @Param("searchPattern") String searchPattern,
                                 @Param("sortBy") String sortBy,
                                 @Param("direction") String direction,
                                 Pageable pageable);

    /** Para o job: marcar como OVERDUE invoices vencidas e ainda abertas */
    @Query("""
        select i from Invoice i
        where i.status in ('PENDING', 'EXTERNAL_CREATED', 'AWAITING_PAYMENT', 'PARTIALLY_PAID')
          and i.dueDate < :today
    """)
    List<Invoice> findOverdue(@Param("today") LocalDate today);

    /**
     * Para o job de reconciliação: busca invoices com cobrança externa ativa
     * criadas há mais de {@code minAgeHours} horas e que ainda não foram pagas/canceladas.
     */
    @Query("""
        select i from Invoice i
        where i.status in ('EXTERNAL_CREATED', 'AWAITING_PAYMENT')
          and i.externalChargeId is not null
          and i.externalProvider = 'ASAAS'
          and i.createdAt < :cutoff
    """)
    List<Invoice> findPendingExternalChargesOlderThan(@Param("cutoff") java.time.Instant cutoff);

    @Query("""
        select count(i), sum(i.amount),
               sum(case when i.status = 'PAID' then i.paidAmount else 0 end),
               sum(case when i.status in ('PENDING', 'EXTERNAL_CREATED', 'AWAITING_PAYMENT', 'PARTIALLY_PAID') then i.amount else 0 end),
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

    @Query("""
        select count(i), sum(i.amount),
               sum(case when i.status = 'PAID' then coalesce(i.paidAmount, i.amount) else 0 end),
               sum(case when i.status in ('PENDING', 'EXTERNAL_CREATED', 'AWAITING_PAYMENT', 'PARTIALLY_PAID') then i.amount else 0 end),
               sum(case when i.status = 'OVERDUE' then i.amount else 0 end)
        from Invoice i
        where i.tenantId = :tenantId
          and (:condoId is null or i.condominiumId = :condoId)
          and (:unitId is null or i.unitId = :unitId)
          and (:referenceMonthFrom is null or i.referenceMonth >= :referenceMonthFrom)
          and (:referenceMonthTo is null or i.referenceMonth <= :referenceMonthTo)
    """)
    Object[] summaryAdvanced(@Param("tenantId") String tenantId,
                             @Param("condoId") Long condoId,
                             @Param("unitId") Long unitId,
                             @Param("referenceMonthFrom") String referenceMonthFrom,
                             @Param("referenceMonthTo") String referenceMonthTo);

    @Query("""
        select i.status, count(i), sum(i.amount)
        from Invoice i
        where i.tenantId = :tenantId
          and (:condoId is null or i.condominiumId = :condoId)
          and (:unitId is null or i.unitId = :unitId)
          and (:referenceMonthFrom is null or i.referenceMonth >= :referenceMonthFrom)
          and (:referenceMonthTo is null or i.referenceMonth <= :referenceMonthTo)
        group by i.status
    """)
    List<Object[]> summaryByStatus(@Param("tenantId") String tenantId,
                                   @Param("condoId") Long condoId,
                                   @Param("unitId") Long unitId,
                                   @Param("referenceMonthFrom") String referenceMonthFrom,
                                   @Param("referenceMonthTo") String referenceMonthTo);

    @Query("""
        select coalesce(u.block, 'Sem bloco'),
               sum(case when i.status = 'OVERDUE' then 1 else 0 end),
               sum(case when i.status = 'OVERDUE' then i.amount else 0 end),
               sum(case when i.status in ('PENDING', 'EXTERNAL_CREATED', 'AWAITING_PAYMENT', 'PARTIALLY_PAID', 'OVERDUE') then i.amount else 0 end)
        from Invoice i, Unit u
        where u.id = i.unitId
          and u.tenantId = i.tenantId
          and u.condominiumId = i.condominiumId
          and i.tenantId = :tenantId
          and (:condoId is null or i.condominiumId = :condoId)
          and (:unitId is null or i.unitId = :unitId)
          and (:referenceMonthFrom is null or i.referenceMonth >= :referenceMonthFrom)
          and (:referenceMonthTo is null or i.referenceMonth <= :referenceMonthTo)
        group by u.block
        order by coalesce(u.block, 'Sem bloco')
    """)
    List<Object[]> delinquencyByBlock(@Param("tenantId") String tenantId,
                                      @Param("condoId") Long condoId,
                                      @Param("unitId") Long unitId,
                                      @Param("referenceMonthFrom") String referenceMonthFrom,
                                      @Param("referenceMonthTo") String referenceMonthTo);

    @Query("""
        select i.referenceMonth,
               count(i),
               sum(i.amount),
               sum(case when i.status = 'PAID' then coalesce(i.paidAmount, i.amount) else 0 end),
               sum(case when i.status = 'OVERDUE' then i.amount else 0 end)
        from Invoice i
        where i.tenantId = :tenantId
          and (:condoId is null or i.condominiumId = :condoId)
          and (:unitId is null or i.unitId = :unitId)
          and (:referenceMonthFrom is null or i.referenceMonth >= :referenceMonthFrom)
          and (:referenceMonthTo is null or i.referenceMonth <= :referenceMonthTo)
        group by i.referenceMonth
        order by i.referenceMonth desc
    """)
    List<Object[]> summaryByReferenceMonth(@Param("tenantId") String tenantId,
                                           @Param("condoId") Long condoId,
                                           @Param("unitId") Long unitId,
                                           @Param("referenceMonthFrom") String referenceMonthFrom,
                                           @Param("referenceMonthTo") String referenceMonthTo);
}
