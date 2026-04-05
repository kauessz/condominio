package com.example.condo.repo;

import com.example.condo.entity.Visitor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface VisitorRepository extends JpaRepository<Visitor, Long> {

  @Query("""
         select v from Visitor v
          where v.tenantId = :t
            and v.condominiumId = :c
            and (:unitId is null or v.unitId = :unitId)
            and v.checkInAt >= coalesce(:from, v.checkInAt)
            and v.checkInAt <= coalesce(:to,   v.checkInAt)
            and (:status is null or v.status = :status)
            and (:type   is null or v.type   = :type)
            and v.deletedAt is null
            and (
                 :q is null or :q = '' or
                 lower(v.name)                   like lower(concat('%', :q, '%')) or
                 lower(coalesce(v.document, '')) like lower(concat('%', :q, '%')) or
                 lower(coalesce(v.phone,    '')) like lower(concat('%', :q, '%')) or
                 lower(coalesce(v.plate,    '')) like lower(concat('%', :q, '%')) or
                 lower(coalesce(v.carrier,  '')) like lower(concat('%', :q, '%'))
            )
         """)
  Page<Visitor> search(@Param("t") String tenantId,
                       @Param("c") Long condoId,
                       @Param("unitId") Long unitId,
                       @Param("q") String q,
                       @Param("from") Instant from,
                       @Param("to") Instant to,
                       @Param("status") Visitor.Status status,
                       @Param("type") Visitor.Type type,
                       Pageable pageable);

  Page<Visitor> findByTenantIdAndCondominiumId(String tenantId, Long condominiumId, Pageable pageable);

  Page<Visitor> findByTenantIdAndCondominiumIdAndUnitId(String tenantId, Long condominiumId, Long unitId,
                                                         Pageable pageable);

  Optional<Visitor> findByTenantIdAndId(String tenantId, Long id);

  @Query("""
         select count(v) from Visitor v
          where v.tenantId = :t
            and v.condominiumId = :c
            and v.status = com.example.condo.entity.Visitor$Status.PENDING
            and v.deletedAt is null
         """)
  long countPendingByCondo(@Param("t") String tenantId,
                           @Param("c") Long condoId);

  @Query("""
         select count(v) from Visitor v
          where v.tenantId = :t
            and v.condominiumId = :c
            and v.type = com.example.condo.entity.Visitor$Type.DELIVERY
            and v.status = com.example.condo.entity.Visitor$Status.PENDING
            and v.deletedAt is null
         """)
  long countPendingDeliveriesByCondo(@Param("t") String tenantId,
                                     @Param("c") Long condoId);

  @Query("""
         select coalesce(sum(v.packages), 0) from Visitor v
          where v.tenantId = :t
            and v.condominiumId = :c
            and v.type = com.example.condo.entity.Visitor$Type.DELIVERY
            and v.status = com.example.condo.entity.Visitor$Status.PENDING
            and v.deletedAt is null
         """)
  long sumPendingDeliveryPackagesByCondo(@Param("t") String tenantId,
                                         @Param("c") Long condoId);
}