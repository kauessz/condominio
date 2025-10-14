package com.example.condo.repo;

import com.example.condo.entity.Visitor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface VisitorRepository extends JpaRepository<Visitor, Long> {

  @Query("""
         select v from Visitor v
          where v.tenantId = :t and v.condominiumId = :c
            and (:unitId is null or v.unitId = :unitId)
            and (:from is null or v.checkInAt >= :from)
            and (:to   is null or v.checkInAt <  :to)
            and (
                 :q is null or :q = '' or
                 lower(v.name)     like lower(concat('%', :q, '%')) or
                 lower(v.document) like lower(concat('%', :q, '%')) or
                 lower(v.phone)    like lower(concat('%', :q, '%'))
            )
         """)
  Page<Visitor> search(@Param("t") String tenantId,
                       @Param("c") Long condoId,
                       @Param("unitId") Long unitId,
                       @Param("q") String q,
                       @Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to,
                       Pageable pageable);

  Page<Visitor> findByTenantIdAndCondominiumId(String tenantId, Long condominiumId, Pageable pageable);

  Page<Visitor> findByTenantIdAndCondominiumIdAndUnitId(String tenantId, Long condominiumId, Long unitId,
                                                         Pageable pageable);

  Optional<Visitor> findByTenantIdAndId(String tenantId, Long id);

  // ===== contador de pendentes por condomínio (fixa PENDING na query) =====
  @Query("""
         select count(v) from Visitor v
          where v.tenantId = :t
            and v.condominiumId = :c
            and v.status = com.example.condo.entity.Visitor$Status.PENDING
            and v.deletedAt is null
         """)
  long countPendingByCondo(@Param("t") String tenantId,
                           @Param("c") Long condoId);
}