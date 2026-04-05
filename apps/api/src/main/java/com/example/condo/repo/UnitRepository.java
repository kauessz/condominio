package com.example.condo.repo;

import com.example.condo.entity.Unit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UnitRepository extends JpaRepository<Unit, Long> {

  @Query("""
      select u from Unit u
      where u.tenantId = :t and u.condominiumId = :c and
            ( :q is null or :q = '' or
              lower(u.number) like lower(concat('%', :q, '%')) or
              lower(u.block)  like lower(concat('%', :q, '%')) )
      """)
  Page<Unit> search(@Param("t") String tenantId,
                    @Param("c") Long condoId,
                    @Param("q") String q,
                    Pageable pageable);

  Optional<Unit> findByTenantIdAndId(String tenantId, Long id);

  // >>> AJUSTADO: trata bloco vazio/NULL corretamente
  @Query("""
     select (count(u) > 0) from Unit u
     where u.tenantId = :t
       and u.condominiumId = :c
       and lower(u.number) = lower(:n)
       and (
            (:b is null and (u.block is null or u.block = ''))
         or (:b is not null and lower(u.block) = :b)
       )
       and (:ignoreId is null or u.id <> :ignoreId)
     """)
  boolean existsDuplicate(@Param("t") String tenantId,
                          @Param("c") Long condoId,
                          @Param("n") String number,
                          @Param("b") String blockLower,
                          @Param("ignoreId") Long ignoreId);

  long countByTenantIdAndCondominiumId(String tenantId, Long condominiumId);

  @Transactional
  void deleteByTenantIdAndCondominiumId(String tenantId, Long condominiumId);

  // ===== Novo: valida se a unidade pertence ao condomínio (para o fluxo de visitantes) =====
  boolean existsByTenantIdAndIdAndCondominiumId(String tenantId, Long id, Long condominiumId);

  // ===== Novo: busca com contagem de moradores por unidade =====
  public interface UnitCountView {
    Long getId();
    String getNumber();
    String getBlock();
    Long getResidentCount();
  }

  @Query("""
    select u.id as id,
           u.number as number,
           u.block as block,
           count(r) as residentCount
    from Unit u
    left join Resident r
      on r.tenantId = u.tenantId
     and r.unitId = u.id
     and r.condominiumId = u.condominiumId
    where u.tenantId = :t
      and u.condominiumId = :c
      and ( :q is null or :q = '' or
            lower(u.number) like lower(concat('%', :q, '%')) or
            lower(u.block)  like lower(concat('%', :q, '%')) )
    group by u.id, u.number, u.block
  """)
  Page<UnitCountView> searchWithCount(@Param("t") String tenantId,
                                      @Param("c") Long condoId,
                                      @Param("q") String q,
                                      Pageable pageable);
}