package com.example.condo.repo;

import com.example.condo.entity.Unit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  @Query("""
     select (count(u) > 0) from Unit u
     where u.tenantId = :t
       and u.condominiumId = :c
       and lower(u.number) = lower(:n)
       and lower(u.block)  = lower(:b)
       and (:ignoreId is null or u.id <> :ignoreId)
     """)
  boolean existsDuplicate(@Param("t") String tenantId,
                          @Param("c") Long condoId,
                          @Param("n") String number,
                          @Param("b") String blockLower,
                          @Param("ignoreId") Long ignoreId);
}