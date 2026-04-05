package com.example.condo.repo;

import com.example.condo.entity.Resident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResidentRepository extends JpaRepository<Resident, Long> {

  @Query("""
      select r from Resident r
      where r.tenantId = :t and r.condominiumId = :c and
            ( :q is null or :q = '' or
              lower(r.name)  like lower(concat('%', :q, '%')) or
              lower(r.email) like lower(concat('%', :q, '%')) or
              lower(r.phone) like lower(concat('%', :q, '%')) )
      """)
  Page<Resident> search(@Param("t") String tenantId,
                        @Param("c") Long condoId,
                        @Param("q") String q,
                        Pageable pageable);

  Optional<Resident> findByTenantIdAndId(String tenantId, Long id);

  long countByTenantIdAndUnitId(String tenantId, Long unitId);

  long countByTenantIdAndCondominiumId(String tenantId, Long condominiumId);

  // JOIN para evitar N+1 na listagem
  @Query("""
    select r as resident, u as unit
    from Resident r
    left join Unit u
      on u.id = r.unitId
     and u.tenantId = r.tenantId
    where r.tenantId = :t and r.condominiumId = :c and
          ( :q is null or :q = '' or
            lower(r.name)  like lower(concat('%', :q, '%')) or
            lower(r.email) like lower(concat('%', :q, '%')) or
            lower(r.phone) like lower(concat('%', :q, '%')) )
  """)
  Page<Object[]> searchWithUnit(@Param("t") String tenantId,
                                @Param("c") Long condoId,
                                @Param("q") String q,
                                Pageable pageable);

  // agregado para /residents/count-by-unit
  @Query("""
    select r.unitId as unitId, count(r) as cnt
    from Resident r
    where r.tenantId = :t and r.condominiumId = :c and r.unitId is not null
    group by r.unitId
  """)
  List<Object[]> countByUnit(@Param("t") String tenantId,
                             @Param("c") Long condoId);
}